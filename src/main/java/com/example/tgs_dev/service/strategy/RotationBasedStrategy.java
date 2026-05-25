package com.example.tgs_dev.service.strategy;

import com.example.tgs_dev.entity.RotationEntry;
import com.example.tgs_dev.entity.Route;
import com.example.tgs_dev.entity.enums.SchedulingMode;
import com.example.tgs_dev.entity.enums.ShiftDayType;
import com.example.tgs_dev.service.VehicleRotationService;
import com.example.tgs_dev.util.DateUtils;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
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
 * <h3>Performance note</h3>
 * Each call to {@link #resolve} issues one DB query to load the full rotation
 * for the day, then filters to the requested route in memory.  When
 * {@code initAllOperations} processes N routes, N such queries are executed.
 * This is intentional — it keeps the strategy interface simple and route-centric.
 * For large fleets (N &gt; ~50 routes) a batch-resolve optimisation can be added
 * as a {@code default} method on {@link ScheduleInitStrategy}.
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
     * Loads all rotation entries for the given day type and date, then returns
     * only those belonging to {@code route}.
     *
     * <p>The stream upcast ({@code .<AssignmentSlot>map(e -> e)}) is an explicit
     * widening from {@code RotationEntry} to {@code AssignmentSlot}; no heap
     * pollution and no unchecked warnings.
     */
    @Override
    public List<AssignmentSlot> resolve(Route route, LocalDate date) {
        ShiftDayType dayType = DateUtils.getTypeofDay(date);
        List<RotationEntry> allEntries = vehicleRotationService.getRotationFromDate(dayType, date);
        return groupByRoute(allEntries)
                .getOrDefault(route.getRouteNumber(), List.of())
                .stream()
                .<AssignmentSlot>map(e -> e)
                .toList();
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    /**
     * Groups a flat list of {@link RotationEntry} by their template's route
     * number.
     *
     * <p>Package-private to enable direct unit testing without going through
     * the full {@link #resolve} call chain.
     */
    Map<String, List<RotationEntry>> groupByRoute(List<RotationEntry> entries) {
        return entries.stream()
                .collect(Collectors.groupingBy(
                        e -> e.getScheduleTemplate().getRoute().getRouteNumber()));
    }
}
