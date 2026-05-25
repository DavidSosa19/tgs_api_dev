package com.example.tgs_dev.service;

import com.example.tgs_dev.entity.Company;
import com.example.tgs_dev.entity.Route;
import com.example.tgs_dev.entity.RouteOperation;
import com.example.tgs_dev.entity.VehicleAssignment;
import com.example.tgs_dev.entity.enums.SchedulingMode;
import com.example.tgs_dev.service.strategy.AssignmentSlot;
import com.example.tgs_dev.service.strategy.ScheduleInitStrategy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Orchestrates the full daily-schedule initialisation pipeline for a company:
 *
 * <pre>
 *   ScheduleInitStrategy.resolve(route, date)
 *     → RouteOperationService.initRoutOperation(route, date)
 *     → VehicleAssignmentService.assignVehicles(slots, operation)
 *     → ScheduleService.calculateVehicleSchedules(assignments)
 * </pre>
 *
 * <h3>Strategy dispatch</h3>
 * The concrete {@link ScheduleInitStrategy} is chosen at runtime based on the
 * current company's {@link SchedulingMode}.  Spring auto-collects every
 * {@code @Component} that implements {@code ScheduleInitStrategy} into the
 * {@code strategyList} constructor parameter; adding a new strategy requires
 * zero changes here.
 *
 * <h3>Transaction boundaries</h3>
 * {@link #initAllOperations} and {@link #initOperation} are the real transaction
 * boundaries.  The private helper {@code initDailyOperation} must always be
 * called within an active transaction; it is not annotated with
 * {@code @Transactional} because same-class self-invocations bypass the Spring
 * proxy.
 */
@Service
public class OperationOrchestratorService {

    private final RouteOperationService                  routeOperationService;
    private final VehicleAssignmentService               vehicleAssignmentService;
    private final ScheduleService                        scheduleService;
    private final RouteService                           routeService;
    private final TenantService                          tenantService;
    private final Map<SchedulingMode, ScheduleInitStrategy> strategies;

    /**
     * @param strategyList all {@link ScheduleInitStrategy} beans registered in
     *                     the application context; collected by Spring automatically.
     */
    public OperationOrchestratorService(
            RouteOperationService      routeOperationService,
            VehicleAssignmentService   vehicleAssignmentService,
            ScheduleService            scheduleService,
            RouteService               routeService,
            TenantService              tenantService,
            List<ScheduleInitStrategy> strategyList) {

        this.routeOperationService    = routeOperationService;
        this.vehicleAssignmentService = vehicleAssignmentService;
        this.scheduleService          = scheduleService;
        this.routeService             = routeService;
        this.tenantService            = tenantService;
        this.strategies = strategyList.stream()
                .collect(Collectors.toUnmodifiableMap(
                        ScheduleInitStrategy::mode,
                        Function.identity()));
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Initialises one {@link RouteOperation} (with assignments and schedules)
     * for every route that belongs to the current company on {@code date}.
     */
    @Transactional
    public void initAllOperations(LocalDate date) {
        ScheduleInitStrategy strategy = currentStrategy();
        routeService.findAll().forEach(route -> initDailyOperation(route, date, strategy));
    }

    /**
     * Initialises a {@link RouteOperation} for a single {@code route} on
     * {@code date}.
     */
    @Transactional
    public void initOperation(Route route, LocalDate date) {
        initDailyOperation(route, date, currentStrategy());
    }

    // ── Internal pipeline ────────────────────────────────────────────────────

    /**
     * Core pipeline step.  Must be called within an active transaction.
     * <p>Calls are intentionally separated so each stage can be mocked and
     * verified independently in tests.
     */
    private void initDailyOperation(Route route, LocalDate date, ScheduleInitStrategy strategy) {
        List<AssignmentSlot>    slots       = strategy.resolve(route, date);
        RouteOperation                 operation   = routeOperationService.initRoutOperation(route, date);
        List<VehicleAssignment>        assignments = vehicleAssignmentService.assignVehicles(slots, operation);
        scheduleService.calculateVehicleSchedules(assignments);
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
