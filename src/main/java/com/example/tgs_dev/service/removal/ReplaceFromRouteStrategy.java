package com.example.tgs_dev.service.removal;

import com.example.tgs_dev.controller.exception.BusinessException;
import com.example.tgs_dev.entity.Route;
import com.example.tgs_dev.entity.RouteOperation;
import com.example.tgs_dev.entity.Schedule;
import com.example.tgs_dev.entity.VehicleAssignment;
import com.example.tgs_dev.entity.enums.RemovalType;
import com.example.tgs_dev.entity.enums.ScheduleOrigin;
import com.example.tgs_dev.service.RouteOperationService;
import com.example.tgs_dev.service.RouteService;
import com.example.tgs_dev.service.ScheduleService;
import com.example.tgs_dev.service.VehicleAssignmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import static com.example.tgs_dev.service.removal.RecalculateHeadwayStrategy.resolveOriginalTime;

/**
 * Replaces a removed vehicle with the last active vehicle from a donor route's
 * operation on the same service date.
 *
 * <h3>Lifecycle effects</h3>
 * <ol>
 *   <li><b>Eliminated vehicle</b> — schedules with
 *       {@code departureTime < fromTime} stay {@code active=true} as
 *       historical record bound to the eliminated VA.  Schedules with
 *       {@code departureTime >= fromTime} are marked {@code active=false}
 *       with reason {@link ScheduleSupersededReason#REPLACED}.</li>
 *   <li><b>Replacement vehicle</b> — a new {@link VehicleAssignment} is created
 *       with the donor vehicle, inheriting the eliminated vehicle's
 *       {@code template} and {@code rowOrder}.  For each superseded schedule
 *       of the eliminated vehicle, a new {@link Schedule} row is created with
 *       origin {@link ScheduleOrigin#REPLACEMENT}, the same
 *       {@code departureTime}, {@code departureOrder} and {@code tripNumber}.
 *       Its {@code originalDepartureTime} points to the eliminated vehicle's
 *       ORIGINAL row at the same trip — so the front sees the slot's true
 *       initial plan, not the eliminated vehicle's intermediate state.</li>
 *   <li><b>Donor vehicle</b> — schedules of the donor's assignment with
 *       {@code departureTime >= fromTime} in its <em>donor</em> operation are
 *       marked {@code active=false} with reason
 *       {@link ScheduleSupersededReason#LOANED} (the donor won't perform those
 *       trips because it has been loaned out).  Donor schedules before
 *       {@code fromTime} stay active (they did happen).</li>
 *   <li>The eliminated and donor {@link VehicleAssignment}s are soft-deleted.
 *       The eliminated's {@code replacedById} points to the replacement.</li>
 * </ol>
 *
 * <h3>Guards</h3>
 * <ul>
 *   <li>{@code fromTime} and {@code sourceRouteGroupId} are required.</li>
 *   <li>The donor route must differ from the operation's route.</li>
 *   <li>The donor route must have an operation on the same service date with
 *       at least one active assignment.</li>
 * </ul>
 */
@Component
public class ReplaceFromRouteStrategy implements VehicleRemovalStrategy {

    private static final Logger log = LoggerFactory.getLogger(ReplaceFromRouteStrategy.class);

    private final VehicleAssignmentService vehicleAssignmentService;
    private final ScheduleService          scheduleService;
    private final RouteService             routeService;
    private final RouteOperationService    routeOperationService;

    public ReplaceFromRouteStrategy(VehicleAssignmentService vehicleAssignmentService,
                                    ScheduleService scheduleService,
                                    RouteService routeService,
                                    RouteOperationService routeOperationService) {
        this.vehicleAssignmentService = vehicleAssignmentService;
        this.scheduleService          = scheduleService;
        this.routeService             = routeService;
        this.routeOperationService    = routeOperationService;
    }

    @Override
    public RemovalType supports() {
        return RemovalType.REMOVE_REPLACE;
    }

    @Override
    public RemovalOutcome execute(RemovalContext ctx) {
        validateInput(ctx);

        VehicleAssignment toRemove    = ctx.assignment();
        RouteOperation    operation   = toRemove.getRouteOperation();
        LocalDate         serviceDate = operation.getServiceDate();
        LocalTime         fromTime    = ctx.fromTime();

        Route donorRoute = routeService.findByGroupId(ctx.sourceRouteGroupId());
        if (donorRoute.getId().equals(operation.getRoute().getId())) {
            throw new BusinessException("donorRoute.sameAsTarget");
        }

        RouteOperation donorOperation = routeOperationService
                .findByRouteAndDate(donorRoute, serviceDate)
                .orElseThrow(() -> new NoSuchElementException(
                        "notFound.routeOperation.donor|" + ctx.sourceRouteGroupId() + "|" + serviceDate));

        VehicleAssignment donorAssignment = vehicleAssignmentService
                .findLastByRouteOperation(donorOperation)
                .orElseThrow(() -> new NoSuchElementException(
                        "notFound.vehicleAssignment.donor|" + ctx.sourceRouteGroupId()));

        // 1. Create the replacement assignment
        VehicleAssignment replacement = new VehicleAssignment(
                operation,
                donorAssignment.getVehicle(),
                toRemove.getScheduleTemplate(),
                toRemove.getRowOrder()
        );
        replacement.setOrigin(VehicleAssignmentOrigin.REPLACEMENT);
        replacement.setReplacesId(toRemove.getId().longValue());
        VehicleAssignment savedReplacement = vehicleAssignmentService.save(replacement);

        // Two-phase save: UPDATEs (supersedes) must hit the DB before INSERTs
        // (REPLACEMENT rows) to avoid a partial-unique-index conflict on
        // (vehicle_assignment_id, departure_order) WHERE active=true.
        // Note: REPLACEMENT rows live under savedReplacement.id, so they don't
        // actually conflict with the eliminated VA's old rows — but donor
        // assignments and other future safeguards are simpler with the same
        // ordering discipline.
        List<Schedule> toSupersede = new ArrayList<>();
        List<Schedule> toCreate    = new ArrayList<>();

        // 2. Eliminated vehicle's schedules >= fromTime → supersede REPLACED + create REPLACEMENT rows
        int inheritedCount = 0;
        for (Schedule old : scheduleService.findAllByAssignment(List.of(toRemove.getId()))) {
            if (!Boolean.TRUE.equals(old.getActive()))           continue;
            if (old.getDepartureTime().isBefore(fromTime))       continue;

            LocalTime originalTime = resolveOriginalTime(old);
            old.supersede(ScheduleSupersededReason.REPLACED, ctx.now());
            toSupersede.add(old);

            Schedule inherited = new Schedule(
                    savedReplacement,
                    old.getDepartureOrder(),
                    old.getTripNumber(),
                    old.getDepartureTime()    // inherit unchanged
            );
            inherited.setCompany(old.getCompany());
            inherited.setOrigin(ScheduleOrigin.REPLACEMENT);
            inherited.setOriginalDepartureTime(originalTime);
            toCreate.add(inherited);
            inheritedCount++;
        }

        // 3. Donor schedules >= fromTime → supersede LOANED (donor won't run these trips)
        int loanedCount = 0;
        for (Schedule donorSched : scheduleService.findAllByAssignment(List.of(donorAssignment.getId()))) {
            if (!Boolean.TRUE.equals(donorSched.getActive()))         continue;
            if (donorSched.getDepartureTime().isBefore(fromTime))     continue;
            donorSched.supersede(ScheduleSupersededReason.LOANED, ctx.now());
            toSupersede.add(donorSched);
            loanedCount++;
        }

        // Phase 1: flush UPDATEs first
        if (!toSupersede.isEmpty()) scheduleService.saveAllAndFlush(toSupersede);
        // Phase 2: INSERTs
        if (!toCreate.isEmpty())    scheduleService.saveAll(toCreate);

        // 4. Soft-delete original and donor (bidirectional link on original)
        toRemove.setReplacedById(savedReplacement.getId().longValue());
        vehicleAssignmentService.softDeleteWithReason(toRemove, VehicleRemovalReason.REPLACED);
        vehicleAssignmentService.softDeleteWithReason(donorAssignment, VehicleRemovalReason.LOANED);

        log.info("REMOVE_REPLACE: assignment {} replaced by {} (donor assignment {}); "
                 + "{} schedules inherited, {} donor schedules marked LOANED",
                 toRemove.getId(), savedReplacement.getId(), donorAssignment.getId(),
                 inheritedCount, loanedCount);

        return RemovalOutcome.withReplacement(savedReplacement.getId());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void validateInput(RemovalContext ctx) {
        if (ctx.fromTime() == null)            throw new BusinessException("fromTime.required");
        if (ctx.sourceRouteGroupId() == null)  throw new BusinessException("sourceRouteGroupId.required");
    }
}
