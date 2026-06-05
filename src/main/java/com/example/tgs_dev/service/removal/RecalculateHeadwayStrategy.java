package com.example.tgs_dev.service.removal;

import com.example.tgs_dev.controller.exception.BusinessException;
import com.example.tgs_dev.entity.Schedule;
import com.example.tgs_dev.entity.VehicleAssignment;
import com.example.tgs_dev.entity.enums.RecalculationScope;
import com.example.tgs_dev.entity.enums.RemovalType;
import com.example.tgs_dev.entity.enums.ScheduleOrigin;
import com.example.tgs_dev.service.ScheduleService;
import com.example.tgs_dev.service.VehicleAssignmentService;
import com.example.tgs_dev.service.removal.recalculation.RecalculationAlgorithm;
import com.example.tgs_dev.service.removal.recalculation.RecalculationContext;
import com.example.tgs_dev.service.removal.recalculation.StretchedHeadwayShifter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toUnmodifiableMap;

/**
 * Removes a vehicle and recalculates the affected vehicles' schedules using
 * the operator-chosen {@link RecalculationScope}.
 *
 * <h3>Lifecycle of affected schedules</h3>
 * <ul>
 *   <li>The removed vehicle's schedules with {@code departureTime < fromTime}
 *       stay {@code active=true} as historical record.  Its schedules with
 *       {@code departureTime >= fromTime} are marked {@code active=false}
 *       with reason {@link ScheduleSupersededReason#VEHICLE_REMOVED}.</li>
 *   <li>For each affected candidate vehicle, the previously-active schedules
 *       at {@code departureTime >= fromTime} are marked {@code active=false}
 *       with reason {@link ScheduleSupersededReason#RECALCULATED}.  A new
 *       row is created in their place with origin
 *       {@link ScheduleOrigin#RECALCULATED}, the shifted departure time, and
 *       the original time preserved on {@code originalDepartureTime} so the
 *       front can render "was X, now Y" diffs trivially.</li>
 * </ul>
 *
 * <h3>Algorithm delegation</h3>
 * The math (how many minutes each candidate shifts) is delegated to a
 * {@link RecalculationAlgorithm} matching the operator's chosen scope.
 * This strategy owns the lifecycle bookkeeping; algorithms remain pure math.
 */
@Component
public class RecalculateHeadwayStrategy implements VehicleRemovalStrategy {

    private static final Logger log = LoggerFactory.getLogger(RecalculateHeadwayStrategy.class);

    private final VehicleAssignmentService                       vehicleAssignmentService;
    private final ScheduleService                                scheduleService;
    private final Map<RecalculationScope, RecalculationAlgorithm> algorithms;

    public RecalculateHeadwayStrategy(VehicleAssignmentService vehicleAssignmentService,
                                      ScheduleService scheduleService,
                                      List<RecalculationAlgorithm> algorithms) {
        this.vehicleAssignmentService = vehicleAssignmentService;
        this.scheduleService          = scheduleService;
        this.algorithms = algorithms.stream()
                .collect(toUnmodifiableMap(RecalculationAlgorithm::scope, a -> a));
    }

    @Override
    public RemovalType supports() {
        return RemovalType.REMOVE_RECALCULATE;
    }

    @Override
    public RemovalOutcome execute(RemovalContext ctx) {
        validateInput(ctx);

        VehicleAssignment toRemove = ctx.assignment();
        LocalTime         fromTime = ctx.fromTime();
        int               rowOrder = toRemove.getRowOrder();

        // 1. Load subsequent candidates (sorted by rowOrder asc)
        List<VehicleAssignment> candidates = vehicleAssignmentService
                .findByRouteOperationAndRowOrderGreaterThan(toRemove.getRouteOperation(), rowOrder)
                .stream()
                .sorted(Comparator.comparingInt(VehicleAssignment::getRowOrder))
                .toList();

        // 2. Batch-load schedules for removed + candidates in a single query
        List<Integer> allIds = new ArrayList<>(candidates.size() + 1);
        allIds.add(toRemove.getId());
        for (VehicleAssignment c : candidates) allIds.add(c.getId());

        Map<Integer, List<Schedule>> activeSchedulesByVa = loadActiveSchedules(allIds, fromTime);

        List<Schedule> removedQualifying = activeSchedulesByVa
                .getOrDefault(toRemove.getId(), List.of());

        // 3. Short-circuit if nothing to recalculate
        if (candidates.isEmpty() || removedQualifying.isEmpty()) {
            inactivateRemovedAndSoftDelete(removedQualifying, toRemove, ctx);
            log.info("REMOVE_RECALCULATE: assignment {} removed; "
                     + "no recalc needed ({} qualifying schedules)",
                     toRemove.getId(), removedQualifying.size());
            return RemovalOutcome.empty();
        }

        Map<Integer, List<Schedule>> candidateQualifying = new HashMap<>(activeSchedulesByVa);
        candidateQualifying.remove(toRemove.getId());

        // 4. Resolve the algorithm and compute shifts
        RecalculationAlgorithm algorithm = algorithms.get(ctx.recalculationScope());
        if (algorithm == null) {
            throw new BusinessException("unsupported.recalculationScope|" + ctx.recalculationScope());
        }

        RecalculationContext recalcCtx = new RecalculationContext(
                removedQualifying,
                candidates,
                candidateQualifying,
                resolveWindowSize(ctx, candidates)
        );

        Map<Integer, Long> shifts = algorithm.computeShifts(recalcCtx);

        // tRemoved = removed vehicle's first qualifying trip — boundary that
        // separates the "absorption cycle" from earlier cycles.  Trips with
        // departureTime < tRemoved belong to a preceding cycle whose slot for
        // the removed vehicle is preserved as history, so no recalculation
        // applies there.
        LocalTime tRemoved = removedQualifying.getFirst().getDepartureTime();

        // 5. Build the supersedes (UPDATEs) and new RECALCULATED rows (INSERTs)
        //    separately.  Hibernate flushes INSERTs before UPDATEs by default,
        //    so we must flush UPDATEs first — otherwise the new row hits the
        //    partial unique index on (va_id, departure_order) WHERE active=true
        //    while the old row is still active.
        List<Schedule> toSupersede = new ArrayList<>();
        List<Schedule> toCreate    = new ArrayList<>();

        // 5a. Mark removed vehicle's qualifying schedules inactive
        for (Schedule s : removedQualifying) {
            s.supersede(ScheduleSupersededReason.VEHICLE_REMOVED, ctx.now());
            toSupersede.add(s);
        }

        // 5b. For each shifted candidate, supersede old and prepare RECALCULATED row.
        //     Only trips >= tRemoved are part of the absorption cycle; earlier
        //     trips stay untouched (their cycle's slot for the removed vehicle
        //     is preserved as history).
        for (Map.Entry<Integer, Long> entry : shifts.entrySet()) {
            Integer vaId  = entry.getKey();
            long    shift = entry.getValue();
            if (shift == 0L) continue;

            List<Schedule> schedules = candidateQualifying.getOrDefault(vaId, List.of());
            for (Schedule old : schedules) {
                if (old.getDepartureTime().isBefore(tRemoved)) continue;   // ← cycle guard

                LocalTime originalTime = resolveOriginalTime(old);
                LocalTime newTime      = StretchedHeadwayShifter.applyShiftSafely(
                        old.getDepartureTime(), shift);

                old.supersede(ScheduleSupersededReason.RECALCULATED, ctx.now());
                toSupersede.add(old);

                Schedule shifted = new Schedule(
                        old.getVehicleAssignment(),
                        old.getDepartureOrder(),
                        old.getTripNumber(),
                        newTime
                );
                shifted.setCompany(old.getCompany());
                shifted.setOrigin(ScheduleOrigin.RECALCULATED);
                shifted.setOriginalDepartureTime(originalTime);
                toCreate.add(shifted);
            }
        }

        // Phase 1: flush UPDATEs (supersedes) to release the partial unique slot
        if (!toSupersede.isEmpty()) scheduleService.saveAllAndFlush(toSupersede);
        // Phase 2: INSERT new rows now that the old ones are inactive in the DB
        if (!toCreate.isEmpty())    scheduleService.saveAll(toCreate);

        // 6. Soft-delete the removed assignment
        vehicleAssignmentService.softDelete(toRemove);

        log.info("REMOVE_RECALCULATE [{}]: assignment {} removed; {} candidates affected, "
                 + "{} schedules superseded, {} new RECALCULATED rows",
                 ctx.recalculationScope(), toRemove.getId(),
                 shifts.size(), toSupersede.size(), toCreate.size());

        return RemovalOutcome.empty();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void validateInput(RemovalContext ctx) {
        if (ctx.fromTime() == null) {
            throw new BusinessException("fromTime.required");
        }
        if (ctx.recalculationScope() == null) {
            throw new BusinessException("recalculationScope.required");
        }
        if (ctx.recalculationScope() == RecalculationScope.SUBSEQUENT_X) {
            if (ctx.windowSize() == null) throw new BusinessException("windowSize.required");
            if (ctx.windowSize() <= 0)    throw new BusinessException("windowSize.mustBePositive");
        }
    }

    private static int resolveWindowSize(RemovalContext ctx, List<VehicleAssignment> candidates) {
        return ctx.recalculationScope() == RecalculationScope.SUBSEQUENT_X
                ? ctx.windowSize()
                : candidates.size();
    }

    /**
     * Loads <em>active</em> schedules for the given assignments with
     * {@code departureTime >= fromTime}, grouped by VA id and sorted ascending
     * by departure time.  Performs a single batch query.
     */
    private Map<Integer, List<Schedule>> loadActiveSchedules(List<Integer> assignmentIds,
                                                              LocalTime fromTime) {
        Map<Integer, List<Schedule>> grouped = new HashMap<>(assignmentIds.size() * 2);
        for (Schedule s : scheduleService.findAllByAssignment(assignmentIds)) {
            if (!Boolean.TRUE.equals(s.getActive())) continue;
            if (s.getDepartureTime().isBefore(fromTime)) continue;
            grouped.computeIfAbsent(s.getVehicleAssignment().getId(), k -> new ArrayList<>()).add(s);
        }
        grouped.values().forEach(list -> list.sort(Comparator.comparing(Schedule::getDepartureTime)));
        return grouped;
    }

    /**
     * Resolves the {@code originalDepartureTime} to copy onto a derived row.
     * <ul>
     *   <li>If the source is {@link ScheduleOrigin#ORIGINAL}, its own departure
     *       time is the original.</li>
     *   <li>Otherwise, walk the lineage via {@code originalDepartureTime} (set
     *       at creation of every derived row).</li>
     *   <li>Defensive fallback: if a non-ORIGINAL row has a null
     *       {@code originalDepartureTime} (data corruption), return its own
     *       departure time — never null.</li>
     * </ul>
     */
    static LocalTime resolveOriginalTime(Schedule source) {
        if (source.getOrigin() == ScheduleOrigin.ORIGINAL) {
            return source.getDepartureTime();
        }
        LocalTime carried = source.getOriginalDepartureTime();
        return carried != null ? carried : source.getDepartureTime();
    }

    /** Used by the short-circuit path — supersedes any qualifying schedules then soft-deletes the VA. */
    private void inactivateRemovedAndSoftDelete(List<Schedule> qualifying,
                                                 VehicleAssignment toRemove,
                                                 RemovalContext ctx) {
        for (Schedule s : qualifying) {
            s.supersede(ScheduleSupersededReason.VEHICLE_REMOVED, ctx.now());
        }
        if (!qualifying.isEmpty()) scheduleService.saveAll(qualifying);
        vehicleAssignmentService.softDelete(toRemove);
    }
}
