package com.example.tgs_dev.service;

import com.example.tgs_dev.entity.Company;
import com.example.tgs_dev.entity.Route;
import com.example.tgs_dev.entity.RouteOperation;
import com.example.tgs_dev.entity.enums.SchedulingMode;
import com.example.tgs_dev.repository.RouteOperationRepository;
import com.example.tgs_dev.service.strategy.AssignmentSlot;
import com.example.tgs_dev.service.strategy.ScheduleInitStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.tgs_dev.controller.exception.BusinessException;
import com.example.tgs_dev.service.InitOperationsResult.RouteInitFailure;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Orchestrates the full daily-schedule initialisation pipeline for a company:
 *
 * <pre>
 *   ScheduleInitStrategy.resolveAll(routes, date)
 *     → for each route (own transaction via OperationInitializer):
 *         RouteOperationService.initRouteOperation(route, date)
 *         VehicleAssignmentService.assignVehicles(slots, operation)
 *         ScheduleService.calculateVehicleSchedules(assignments)
 * </pre>
 *
 * <h3>Strategy dispatch</h3>
 * The concrete {@link ScheduleInitStrategy} is chosen at runtime based on the
 * current company's {@link SchedulingMode}.  Spring auto-collects every
 * {@code @Component} that implements {@code ScheduleInitStrategy} into the
 * {@code strategyList} constructor parameter; adding a new strategy requires
 * zero changes here.
 *
 * <h3>Idempotency</h3>
 * Before initialising, {@link #initAllOperations} filters out routes that already
 * have an active {@link RouteOperation} for the target date.  Re-running the same
 * call is therefore a no-op for those routes.
 *
 * <h3>Transaction boundaries</h3>
 * The orchestrator itself is <strong>not</strong> {@code @Transactional}.  Each
 * route's persistence runs in its own transaction inside
 * {@link OperationInitializer#persistOne}, so a failure on one route does not
 * roll back the routes that were already initialised successfully.  Failures
 * are logged at WARN and the loop continues; the return value reflects only the
 * routes that actually completed.
 */
@Service
public class OperationOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(OperationOrchestratorService.class);

    private final OperationInitializer                   initializer;
    private final RouteService                           routeService;
    private final RouteOperationRepository               routeOperationRepository;
    private final TenantService                          tenantService;
    private final Map<SchedulingMode, ScheduleInitStrategy> strategies;

    /**
     * @param strategyList all {@link ScheduleInitStrategy} beans registered in
     *                     the application context; collected by Spring automatically.
     */
    public OperationOrchestratorService(
            OperationInitializer       initializer,
            RouteService               routeService,
            RouteOperationRepository   routeOperationRepository,
            TenantService              tenantService,
            List<ScheduleInitStrategy> strategyList) {

        this.initializer              = initializer;
        this.routeService             = routeService;
        this.routeOperationRepository = routeOperationRepository;
        this.tenantService            = tenantService;
        this.strategies = strategyList.stream()
                .collect(Collectors.toUnmodifiableMap(
                        ScheduleInitStrategy::mode,
                        Function.identity()));
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Initialises one {@link RouteOperation} for every active route of the current
     * company on {@code date} that does not already have one.
     *
     * <h3>Idempotency</h3>
     * Routes that already have an active operation for {@code date} are counted
     * in {@link InitOperationsResult#skipped()} — re-running this call is safe.
     *
     * <h3>Batch resolution</h3>
     * Strategies that share work across routes (e.g. {@code RotationBasedStrategy}
     * loading one rotation that covers every route of the day) implement
     * {@link ScheduleInitStrategy#resolveAll} to do that work once.  This method
     * dispatches a single call to {@code resolveAll} for the pending routes,
     * eliminating O(N) query amplification.
     *
     * <h3>Partial failure tolerance</h3>
     * Each route is persisted in its own transaction via
     * {@link OperationInitializer#persistOne}.  If one route fails the failure
     * is logged, captured in the result's {@link InitOperationsResult#failures()}
     * list, and the loop continues; routes that already persisted remain committed.
     *
     * @return an {@link InitOperationsResult} carrying per-route outcomes
     */
    public InitOperationsResult initAllOperations(LocalDate date) {
        ScheduleInitStrategy strategy  = currentStrategy();
        List<Route>          allRoutes = routeService.findAll();
        if (allRoutes.isEmpty()) return InitOperationsResult.noop(0);

        // Idempotency — skip routes that already have an active operation for the date.
        Integer companyId = tenantService.currentCompanyId();
        Set<Integer> alreadyInitialised = new HashSet<>(
                routeOperationRepository.findRouteIdsWithActiveOperation(date, companyId));

        List<Route> pending = allRoutes.stream()
                .filter(r -> !alreadyInitialised.contains(r.getId()))
                .toList();

        int skipped = alreadyInitialised.size();
        if (pending.isEmpty()) return InitOperationsResult.noop(skipped);

        // One call covers every pending route — strategies sharing state avoid N+1.
        Map<Route, List<AssignmentSlot>> slotsByRoute = strategy.resolveAll(pending, date);

        int                      initialised = 0;
        List<RouteInitFailure>   failures    = new ArrayList<>();

        for (Entry<Route, List<AssignmentSlot>> e : slotsByRoute.entrySet()) {
            Route route = e.getKey();
            try {
                initializer.persistOne(route, date, e.getValue());
                initialised++;
            } catch (RuntimeException ex) {
                // Per-route transaction has rolled back; capture and continue so the
                // remaining routes still get initialised.
                log.warn("Failed to initialise operation for route {} on {}: {}",
                        route.getRouteNumber(), date, ex.getMessage(), ex);
                failures.add(new RouteInitFailure(
                        route.getGroup() != null ? route.getGroup().getId() : null,
                        route.getRouteNumber(),
                        extractReason(ex)));
            }
        }
        return new InitOperationsResult(initialised, skipped, failures);
    }

    /**
     * Initialises a {@link RouteOperation} for a single {@code route} on
     * {@code date}.  Runs in its own transaction via
     * {@link OperationInitializer#persistOne}.
     */
    public void initOperation(Route route, LocalDate date) {
        List<AssignmentSlot> slots = currentStrategy().resolve(route, date);
        initializer.persistOne(route, date, slots);
    }

    /**
     * Extracts a serialisable reason string from a per-route failure.
     *
     * <p>For {@link BusinessException} we surface the i18n key + parameters that
     * the frontend already knows how to resolve (e.g.
     * {@code validation.period.missingDepartureTimes|15}).  For any other runtime
     * exception we fall back to {@code SimpleName: message} so the operator can
     * still identify the cause without having to dig through server logs.
     */
    private static String extractReason(RuntimeException ex) {
        if (ex instanceof BusinessException) {
            return ex.getMessage();
        }
        String msg = ex.getMessage();
        return ex.getClass().getSimpleName() + (msg != null ? ": " + msg : "");
    }

    // ── Strategy resolution ──────────────────────────────────────────────────

    /**
     * Returns the strategy registered for the current company's
     * {@link SchedulingMode}.
     *
     * @throws IllegalStateException if no strategy bean is registered for the
     *                               mode (indicates a deployment error: a new
     *                               enum value was added without a corresponding
     *                               {@code ScheduleInitStrategy} implementation).
     */
    private ScheduleInitStrategy currentStrategy() {
        Company company  = tenantService.currentCompany();
        ScheduleInitStrategy strategy = strategies.get(company.getSchedulingMode());
        if (strategy == null) {
            throw new IllegalStateException(
                    "No ScheduleInitStrategy registered for mode '"
                    + company.getSchedulingMode()
                    + "' (company id=" + company.getId() + "). "
                    + "Ensure a @Component implementing ScheduleInitStrategy "
                    + "returns this SchedulingMode from mode().");
        }
        return strategy;
    }
}
