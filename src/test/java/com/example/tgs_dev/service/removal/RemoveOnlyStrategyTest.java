package com.example.tgs_dev.service.removal;

import com.example.tgs_dev.entity.Schedule;
import com.example.tgs_dev.entity.VehicleAssignment;
import com.example.tgs_dev.entity.enums.RemovalType;
import com.example.tgs_dev.service.ScheduleService;
import com.example.tgs_dev.service.VehicleAssignmentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static com.example.tgs_dev.TestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RemoveOnlyStrategy")
class RemoveOnlyStrategyTest {

    @Mock VehicleAssignmentService vehicleAssignmentService;
    @Mock ScheduleService          scheduleService;
    @InjectMocks RemoveOnlyStrategy sut;

    @Test @DisplayName("supports REMOVE_ONLY")
    void supports_removeOnly() {
        assertThat(sut.supports()).isEqualTo(RemovalType.REMOVE_ONLY);
    }

    @Test @DisplayName("supersedes only schedules >= fromTime; soft-deletes the assignment")
    void supersedesFromTimeForward() {
        VehicleAssignment va = assignment(1, operation(1, route(1, "1"), OP_DATE),
                                          vehicle(10, "V-001"), template(100, route(1, "1"), 1), 1);
        Schedule before = schedule(101, va, 1, LocalTime.of(6, 0));   // < fromTime → keep
        Schedule equal  = schedule(102, va, 2, LocalTime.of(9, 0));   // = fromTime → supersede
        Schedule after  = schedule(103, va, 3, LocalTime.of(12, 0));  // > fromTime → supersede

        LocalDateTime now = LocalDateTime.now();

        when(scheduleService.findAllByAssignment(List.of(va.getId())))
                .thenReturn(List.of(before, equal, after));

        sut.execute(new RemovalContext(va, now, LocalTime.of(9, 0), null, null, null));

        // before stays active
        assertThat(before.getActive()).isTrue();
        // equal and after get superseded
        assertThat(equal.getActive()).isFalse();
        assertThat(equal.getSupersededReason()).isEqualTo(ScheduleSupersededReason.VEHICLE_REMOVED);
        assertThat(equal.getSupersededAt()).isEqualTo(now);
        assertThat(after.getActive()).isFalse();
        assertThat(after.getSupersededReason()).isEqualTo(ScheduleSupersededReason.VEHICLE_REMOVED);

        // Only the 2 superseded rows persisted
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Schedule>> captor = ArgumentCaptor.forClass(List.class);
        verify(scheduleService).saveAll(captor.capture());
        assertThat(captor.getValue()).containsExactlyInAnyOrder(equal, after);

        verify(vehicleAssignmentService).softDelete(va);
    }

    @Test @DisplayName("no qualifying schedules → no save call, still soft-deletes")
    void noQualifying_stillSoftDeletes() {
        VehicleAssignment va = assignment(1, operation(1, route(1, "1"), OP_DATE),
                                          vehicle(10, "V-001"), template(100, route(1, "1"), 1), 1);
        Schedule before = schedule(101, va, 1, LocalTime.of(6, 0));

        when(scheduleService.findAllByAssignment(List.of(va.getId())))
                .thenReturn(List.of(before));

        sut.execute(new RemovalContext(va, LocalDateTime.now(), LocalTime.of(23, 0), null, null, null));

        assertThat(before.getActive()).isTrue();
        verify(scheduleService, never()).saveAll(org.mockito.ArgumentMatchers.any());
        verify(vehicleAssignmentService).softDelete(va);
    }
}
