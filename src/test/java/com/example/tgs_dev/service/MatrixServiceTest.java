package com.example.tgs_dev.service;

import com.example.tgs_dev.controller.response.AssignmentSchedulesDTO;
import com.example.tgs_dev.entity.RouteOperation;
import com.example.tgs_dev.entity.Schedule;
import com.example.tgs_dev.entity.VehicleAssignment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static com.example.tgs_dev.TestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MatrixService")
class MatrixServiceTest {

    @Mock ScheduleService scheduleService;
    @Mock VehicleAssignmentService vehicleAssignmentService;
    @Mock RouteOperationService routeOperationService;
    @InjectMocks MatrixService sut;

    private RouteOperation op() {
        return operation(1, route(1, "1"), OP_DATE);
    }

    // ── getAssignmentSchedules ────────────────────────────────────────────────

    @Nested @DisplayName("getAssignmentSchedules")
    class GetAssignmentSchedules {

        @Test @DisplayName("returns empty list when operation has no assignments")
        void noAssignments_returnsEmpty() {
            RouteOperation op = op();
            when(routeOperationService.findById(1)).thenReturn(op);
            // getAssignmentSchedules uses findByOperationWithDetails (JOIN FETCH), not findByRouteOperation
            when(vehicleAssignmentService.findByOperationWithDetails(op)).thenReturn(List.of());

            List<AssignmentSchedulesDTO> result = sut.getAssignmentSchedules(1);

            assertThat(result).isEmpty();
            verifyNoInteractions(scheduleService);
        }

        @Test @DisplayName("groups schedules by assignment id and maps to DTOs")
        void withAssignments_groupsSchedules() {
            RouteOperation op = op();
            var r = route(1, "1");
            var t = template(1, r, java.time.LocalTime.of(6, 0));
            VehicleAssignment va1 = assignment(1, op, vehicle(1, "V-1"), t, 1);
            VehicleAssignment va2 = assignment(2, op, vehicle(2, "V-2"), t, 2);

            Schedule s1 = schedule(1, va1, 1, java.time.LocalTime.of(6, 0));
            Schedule s2 = schedule(2, va1, 2, java.time.LocalTime.of(6, 30));
            Schedule s3 = schedule(3, va2, 1, java.time.LocalTime.of(6, 0));

            when(routeOperationService.findById(1)).thenReturn(op);
            // getAssignmentSchedules uses findByOperationWithDetails (JOIN FETCH), not findByRouteOperation
            when(vehicleAssignmentService.findByOperationWithDetails(op)).thenReturn(List.of(va1, va2));
            when(scheduleService.findAllByAssignment(anyList())).thenReturn(List.of(s1, s2, s3));

            List<AssignmentSchedulesDTO> result = sut.getAssignmentSchedules(1);

            assertThat(result).hasSize(2);
            AssignmentSchedulesDTO dto1 = result.stream()
                    .filter(d -> d.vehicleAssignment().getId() == 1).findFirst().orElseThrow();
            AssignmentSchedulesDTO dto2 = result.stream()
                    .filter(d -> d.vehicleAssignment().getId() == 2).findFirst().orElseThrow();
            assertThat(dto1.schedules()).hasSize(2);
            assertThat(dto2.schedules()).hasSize(1);
        }

        @Test @DisplayName("assignment with no schedules gets an empty schedule list")
        void assignmentWithNoSchedules_getsEmptyList() {
            RouteOperation op = op();
            var t = template(1, route(1, "1"), java.time.LocalTime.of(6, 0));
            VehicleAssignment va = assignment(1, op, vehicle(1, "V-1"), t, 1);

            when(routeOperationService.findById(1)).thenReturn(op);
            // getAssignmentSchedules uses findByOperationWithDetails (JOIN FETCH), not findByRouteOperation
            when(vehicleAssignmentService.findByOperationWithDetails(op)).thenReturn(List.of(va));
            when(scheduleService.findAllByAssignment(anyList())).thenReturn(List.of());

            List<AssignmentSchedulesDTO> result = sut.getAssignmentSchedules(1);

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().schedules()).isEmpty();
        }
    }
}
