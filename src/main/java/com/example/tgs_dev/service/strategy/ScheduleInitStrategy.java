package com.example.tgs_dev.service.strategy;

import com.example.tgs_dev.entity.Route;
import com.example.tgs_dev.entity.enums.SchedulingMode;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Strategy for computing ordered vehicle-to-template assignment slots for a
 * single route on a given service date.
 *
 * <p>Each implementation is a Spring {@code @Component} and self-identifies
 * via {@link #mode()}.  The
 * {@link com.example.tgs_dev.service.OperationOrchestratorService} collects
 * all implementations at startup and dispatches to the one matching the
 * current company's {@link SchedulingMode}.
 *
 * <h3>Implementing a new strategy</h3>
 * <pre>{@code
 * @Component
 * public class ManualEntryStrategy implements ScheduleInitStrategy {
 *
 *     @Override
 *     public SchedulingMode mode() { return SchedulingMode.MANUAL_ENTRY; }
 *
 *     @Override
 *     public List<? extends AssignmentSlot> resolve(Route route, LocalDate date) {
 *         // load assignments from your data source ...
 *     }
 * }
 * }</pre>
 *
 * <h3>Contract for {@link #resolve}</h3>
 * <ul>
 *   <li>Must never return {@code null} — return an empty list if there are no
 *       assignments.</li>
 *   <li>Order is significant: index {@code 0} maps to row-order {@code 1} in
 *       the resulting {@link com.example.tgs_dev.entity.VehicleAssignment}.</li>
 *   <li>Always called within an active {@code @Transactional} context started
 *       by the orchestrator — may read from the database.</li>
 * </ul>
 */
public interface ScheduleInitStrategy {

    /**
     * The {@link SchedulingMode} this strategy handles.
     * Must be unique across all registered strategy beans.
     */
    SchedulingMode mode();

    /**
     * Resolves the ordered list of vehicle-to-template slots for {@code route}
     * on {@code date}.
     *
     * @param route the route being initialised
     * @param date  the service date
     * @return ordered assignment slots; never {@code null}
     */
    List<AssignmentSlot> resolve(Route route, LocalDate date);

    /**
     * Batch variant of {@link #resolve(Route, LocalDate)} for resolving multiple
     * routes on the same date efficiently.
     *
     * <p>The default implementation simply iterates {@link #resolve} per route,
     * which is fine for strategies whose per-route cost is independent (e.g.
     * loading from a per-route data source).  Strategies that share work across
     * routes (e.g. {@link RotationBasedStrategy} loading one rotation that covers
     * all routes for the day) <strong>must</strong> override this method to avoid
     * N+1 queries.
     *
     * <p>The returned map preserves the order of {@code routes} via
     * {@link LinkedHashMap}, so callers can iterate in a deterministic sequence.
     *
     * @param routes the routes to resolve, in the desired iteration order
     * @param date   the service date
     * @return a map from each route to its ordered assignment slots; entries
     *         never have {@code null} values (empty list for routes without slots)
     */
    default Map<Route, List<AssignmentSlot>> resolveAll(List<Route> routes, LocalDate date) {
        Map<Route, List<AssignmentSlot>> result = LinkedHashMap.newLinkedHashMap(routes.size());
        for (Route route : routes) {
            result.put(route, resolve(route, date));
        }
        return result;
    }
}
