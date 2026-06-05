package com.example.tgs_dev.service.removal;

import com.example.tgs_dev.controller.exception.BusinessException;
import com.example.tgs_dev.entity.Schedule;
import com.example.tgs_dev.entity.VehicleAssignment;
import com.example.tgs_dev.entity.enums.RemovalType;
import com.example.tgs_dev.service.ScheduleService;
import com.example.tgs_dev.service.VehicleAssignmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.List;

/**
 * Removes a vehicle without recalculating any other vehicle's schedule.
 *
 * <h3>Lifecycle</h3>
 * <ul>
 *   <li>Schedules of the removed assignment with
 *       {@code departureTime < fromTime} stay {@code active=true} as
 *       historical record (the vehicle did run those trips).</li>
 *   <li>Schedules with {@code departureTime >= fromTime} are marked
 *       {@code active=false} with reason
 *       {@link ScheduleSupersededReason#VEHICLE_REMOVED} — the vehicle is
 *       gone, those trips will not happen.</li>
 *   <li>Other vehicles' schedules are not touched — a gap appears in the
 *       departure sequence at the removed slot.</li>
 *   <li>The {@link VehicleAssignment} itself is soft-deleted.</li>
 * </ul>
 */
@Component
public class RemoveOnlyStrategy implements VehicleRemovalStrategy {

    private static final Logger log = LoggerFactory.getLogger(RemoveOnlyStrategy.class);

    private final VehicleAssignmentService vehicleAssignmentService;
    private final ScheduleService          scheduleService;

    public RemoveOnlyStrategy(VehicleAssignmentService vehicleAssignmentService,
                              ScheduleService scheduleService) {
        this.vehicleAssignmentService = vehicleAssignmentService;
        this.scheduleService          = scheduleService;
    }

    @Override
    public RemovalType supports() {
        return RemovalType.REMOVE_ONLY;
    }

    @Override
    public RemovalOutcome execute(RemovalContext ctx) {
        if (ctx.fromTime() == null) {
            throw new BusinessException("fromTime.required");
        }

        VehicleAssignment va       = ctx.assignment();
        LocalTime         fromTime = ctx.fromTime();

        List<Schedule> qualifying = scheduleService
                .findAllByAssignment(List.of(va.getId()))
                .stream()
                .filter(s -> Boolean.TRUE.equals(s.getActive()))
                .filter(s -> !s.getDepartureTime().isBefore(fromTime))
                .toList();

        for (Schedule s : qualifying) {
            s.supersede(ScheduleSupersededReason.VEHICLE_REMOVED, ctx.now());
        }
        if (!qualifying.isEmpty()) {
            scheduleService.saveAll(qualifying);
        }

        vehicleAssignmentService.softDelete(va);

        log.info("REMOVE_ONLY: assignment {} removed; {} schedules marked inactive (fromTime={})",
                 va.getId(), qualifying.size(), fromTime);

        return RemovalOutcome.empty();
    }
}
