package com.example.tgs_dev.service;

import com.example.tgs_dev.entity.Route;
import com.example.tgs_dev.entity.RouteOperation;
import com.example.tgs_dev.entity.VehicleAssignment;
import com.example.tgs_dev.service.strategy.AssignmentSlot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static com.example.tgs_dev.TestFixtures.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link OperationInitializer}.
 *
 * <p>Verifies the pipeline order — operation creation, vehicle assignment,
 * schedule calculation — and that the slots are passed through unchanged.
 * Transactional semantics ({@code REQUIRES_NEW}) are an integration concern
 * and not exercised here.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OperationInitializer")
class OperationInitializerTest {

    @Mock RouteOperationService    routeOperationService;
    @Mock VehicleAssignmentService vehicleAssignmentService;
    @Mock ScheduleService          scheduleService;

    @InjectMocks OperationInitializer sut;

    private Route          routeA;
    private RouteOperation op;

    @BeforeEach
    void setUp() {
        routeA = route(1, "1");
        op     = operation(1, routeA, OP_DATE);
    }

    @Test @DisplayName("creates operation, assigns vehicles, calculates schedules — in order")
    void invokesStagesInOrder() {
        List<AssignmentSlot>    slots       = List.of();
        List<VehicleAssignment> assignments = List.of(new VehicleAssignment());

        when(routeOperationService.initRouteOperation(routeA, OP_DATE)).thenReturn(op);
        when(vehicleAssignmentService.assignVehicles(slots, op)).thenReturn(assignments);

        sut.persistOne(routeA, OP_DATE, slots);

        var ordered = inOrder(routeOperationService, vehicleAssignmentService, scheduleService);
        ordered.verify(routeOperationService).initRouteOperation(routeA, OP_DATE);
        ordered.verify(vehicleAssignmentService).assignVehicles(slots, op);
        ordered.verify(scheduleService).calculateVehicleSchedules(assignments);
    }

    @Test @DisplayName("passes the provided slots through unchanged to the assignment service")
    void passesSlotsThroughUnchanged() {
        AssignmentSlot      customSlot = slot(vehicle(10, "V-001"), template(100, routeA, 1));
        List<AssignmentSlot> slots     = List.of(customSlot);

        when(routeOperationService.initRouteOperation(routeA, OP_DATE)).thenReturn(op);
        when(vehicleAssignmentService.assignVehicles(slots, op)).thenReturn(List.of());

        sut.persistOne(routeA, OP_DATE, slots);

        verify(vehicleAssignmentService).assignVehicles(slots, op);
    }

    @Test @DisplayName("propagates exceptions so the per-route transaction rolls back")
    void propagatesExceptions() {
        when(routeOperationService.initRouteOperation(any(), any()))
                .thenThrow(new RuntimeException("simulated failure"));

        List<AssignmentSlot> emptySlots = List.of();
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> sut.persistOne(routeA, OP_DATE, emptySlots))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("simulated failure");

        verifyNoInteractions(vehicleAssignmentService, scheduleService);
        verify(routeOperationService).initRouteOperation(routeA, OP_DATE);
    }
}
