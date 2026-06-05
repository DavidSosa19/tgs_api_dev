package com.example.tgs_dev.service;

import com.example.tgs_dev.controller.response.AssignmentSchedulesDTO;
import com.example.tgs_dev.controller.response.viewer.OperationScheduleDTO;
import com.example.tgs_dev.entity.RouteOperation;
import com.example.tgs_dev.entity.Schedule;
import com.example.tgs_dev.entity.VehicleAssignment;
import com.example.tgs_dev.repository.projection.ScheduleProjection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.List;
import java.util.NoSuchElementException;

import static com.example.tgs_dev.TestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    // ── getOperationSchedules ─────────────────────────────────────────────────

    @Nested @DisplayName("getOperationSchedules")
    class GetOperationSchedules {

        @Test @DisplayName("returns empty schedule when operation has no assignments")
        void noAssignments_returnsEmptySchedule() {
            RouteOperation op = op();
            when(routeOperationService.findById(1)).thenReturn(op);
            when(vehicleAssignmentService.findByOperationWithDetails(op)).thenReturn(List.of());

            OperationScheduleDTO result = sut.getOperationSchedules(1);

            assertThat(result.vehicleSchedules()).isEmpty();
            assertThat(result.operation().operationId()).isEqualTo(1);
            verifyNoInteractions(scheduleService);
        }

        @Test @DisplayName("maps assignments and projections into VehicleScheduleDTOs")
        void withAssignments_mapsCorrectly() {
            RouteOperation op = op();
            var r = route(1, "1");
            var t = template(1, r, 1);
            VehicleAssignment va1 = assignment(1, op, vehicle(1, "V-001"), t, 1);
            VehicleAssignment va2 = assignment(2, op, vehicle(2, "V-002"), t, 2);

            ScheduleProjection p1 = scheduleProjection(1, 1, 1, LocalTime.of(6, 0));
            ScheduleProjection p2 = scheduleProjection(1, 2, 2, LocalTime.of(7, 0));
            ScheduleProjection p3 = scheduleProjection(2, 3, 1, LocalTime.of(6, 10));

            when(routeOperationService.findById(1)).thenReturn(op);
            when(vehicleAssignmentService.findByOperationWithDetails(op))
                    .thenReturn(List.of(va1, va2));
            when(scheduleService.findScheduleProjections(List.of(1, 2)))
                    .thenReturn(List.of(p1, p2, p3));

            OperationScheduleDTO result = sut.getOperationSchedules(1);

            assertThat(result.vehicleSchedules()).hasSize(2);

            var row1 = result.vehicleSchedules().get(0);
            assertThat(row1.rowOrder()).isEqualTo(1);
            assertThat(row1.vehicle().vehicleNumber()).isEqualTo("V-001");
            assertThat(row1.schedules()).hasSize(2);
            assertThat(row1.schedules().get(0).departureOrder()).isEqualTo(1);
            assertThat(row1.schedules().get(0).tripNumber()).isEqualTo(1);

            var row2 = result.vehicleSchedules().get(1);
            assertThat(row2.vehicle().vehicleNumber()).isEqualTo("V-002");
            assertThat(row2.schedules()).hasSize(1);
        }

        @Test @DisplayName("vehicle with no projections gets an empty schedules list")
        void assignmentWithNoProjections_getsEmptyEntries() {
            RouteOperation op = op();
            var t = template(1, route(1, "1"), 1);
            VehicleAssignment va = assignment(1, op, vehicle(1, "V-001"), t, 1);

            when(routeOperationService.findById(1)).thenReturn(op);
            when(vehicleAssignmentService.findByOperationWithDetails(op))
                    .thenReturn(List.of(va));
            when(scheduleService.findScheduleProjections(List.of(1)))
                    .thenReturn(List.of());

            OperationScheduleDTO result = sut.getOperationSchedules(1);

            assertThat(result.vehicleSchedules()).hasSize(1);
            assertThat(result.vehicleSchedules().getFirst().schedules()).isEmpty();
        }

        @Test @DisplayName("propagates NoSuchElementException when operation not found")
        void notFound_propagatesException() {
            when(routeOperationService.findById(99))
                    .thenThrow(new NoSuchElementException("notFound.routeOperation|99"));

            assertThatThrownBy(() -> sut.getOperationSchedules(99))
                    .isInstanceOf(NoSuchElementException.class);
        }
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
            var t = template(1, r, 1);
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
            var t = template(1, route(1, "1"), 1);
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
