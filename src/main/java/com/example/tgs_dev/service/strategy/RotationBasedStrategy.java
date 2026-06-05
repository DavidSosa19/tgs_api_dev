package com.example.tgs_dev.service.strategy;

import com.example.tgs_dev.entity.RotationEntry;
import com.example.tgs_dev.entity.Route;
import com.example.tgs_dev.entity.enums.SchedulingMode;
import com.example.tgs_dev.entity.enums.ShiftDayType;
import com.example.tgs_dev.service.VehicleRotationService;
import com.example.tgs_dev.util.DateUtils;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Rotation-based schedule initialisation strategy.
 *
 * <p>Resolves vehicle assignments by consulting the active
 * {@link com.example.tgs_dev.entity.VehicleRotation} for the given date,
 * then rotating vehicle positions according to the number of business days
 * elapsed since the rotation's start date (the cartulina model).
 *
 * <h3>Batch resolution</h3>
 * A single {@link com.example.tgs_dev.entity.VehicleRotation} for a given day type
 * covers <em>all</em> routes of the day.  {@link #resolveAll} loads it once and
 * groups its entries by route, eliminating the N+1 pattern that
 * {@code OperationOrchestratorService.initAllOperations} would otherwise produce.
 *
 * <p>Single-route {@link #resolve} is preserved for direct callers
 * ({@code initOperation}), but internally delegates to the same rotation lookup.
 */
@Component
public class RotationBasedStrategy implements ScheduleInitStrategy {

    private final VehicleRotationService vehicleRotationService;

    public RotationBasedStrategy(VehicleRotationService vehicleRotationService) {
        this.vehicleRotationService = vehicleRotationService;
    }

    // ── ScheduleInitStrategy ─────────────────────────────────────────────────

    @Override
    public SchedulingMode mode() {
        return SchedulingMode.ROTATION_BASED;
    }

    /**
     * Loads the rotation for {@code date}'s day type and returns the subset
     * belonging to {@code route}.
     *
     * <p>The stream upcast ({@code .<AssignmentSlot>map(e -> e)}) is an explicit
     * widening from {@code RotationEntry} to {@code AssignmentSlot}; no heap
     * pollution and no unchecked warnings.
     */
    @Override
    public List<AssignmentSlot> resolve(Route route, LocalDate date) {
        List<RotationEntry> allEntries = loadRotation(date);
        return groupByRoute(allEntries)
                .getOrDefault(groupIdOf(route), List.of())
                .stream()
                .<AssignmentSlot>map(e -> e)
                .toList();
    }

    /**
     * Loads the rotation <strong>once</strong> and partitions its entries
     * across all input routes, eliminating the per-route query that
     * {@link #resolve} would otherwise dispatch.
     *
     * <p>The returned map preserves the iteration order of {@code routes}
     * (via {@link LinkedHashMap}) so the orchestrator initialises operations
     * in a deterministic sequence.
     *
     * @param routes the routes to resolve, in the desired iteration order
     * @param date   the service date
     * @return a map from each route to its ordered assignment slots; routes
     *         absent from the rotation receive an empty list
     */
    @Override
    public Map<Route, List<AssignmentSlot>> resolveAll(List<Route> routes, LocalDate date) {
        if (routes.isEmpty()) return Map.of();

        List<RotationEntry>        allEntries = loadRotation(date);
        Map<Long, List<RotationEntry>> byGroupId = groupByRoute(allEntries);

        Map<Route, List<AssignmentSlot>> result = LinkedHashMap.newLinkedHashMap(routes.size());
        for (Route route : routes) {
            List<AssignmentSlot> slots = byGroupId
                    .getOrDefault(groupIdOf(route), List.of())
                    .stream()
                    .<AssignmentSlot>map(e -> e)
                    .toList();
            result.put(route, slots);
        }
        return result;
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    private List<RotationEntry> loadRotation(LocalDate date) {
        ShiftDayType dayType = DateUtils.getTypeofDay(date);
        return vehicleRotationService.getRotationFromDate(dayType, date);
    }

    /**
     * Groups a flat list of {@link RotationEntry} by their template's
     * {@link com.example.tgs_dev.entity.RouteGroup} id — the stable SCD
     * business identity.
     *
     * <p>Why not {@code route.id}: with SCD Type-2 enabled on {@link Route},
     * every update of a route creates a new row with a new surrogate id, while
     * the group id stays the same.  {@link com.example.tgs_dev.service.RouteService#findAll}
     * returns the <em>current</em> versions, whereas templates may still point
     * to historical versions (FK pinned at template creation).  Grouping by
     * {@code route.group.id} matches both sides reliably.
     *
     * <p>Package-private to enable direct unit testing.
     */
    Map<Long, List<RotationEntry>> groupByRoute(List<RotationEntry> entries) {
        return entries.stream()
                .filter(e -> e.getScheduleTemplate() != null
                          && e.getScheduleTemplate().getRoute() != null
                          && e.getScheduleTemplate().getRoute().getGroup() != null)
                .collect(Collectors.groupingBy(
                        e -> e.getScheduleTemplate().getRoute().getGroup().getId()));
    }

    /** Null-safe accessor: returns the {@link com.example.tgs_dev.entity.RouteGroup} id of {@code route}, or {@code null}. */
    private static Long groupIdOf(Route route) {
        return route.getGroup() != null ? route.getGroup().getId() : null;
    }
}
