package com.example.tgs_dev.service;

import com.example.tgs_dev.entity.*;
import com.example.tgs_dev.entity.enums.SchedulingMode;
import com.example.tgs_dev.service.strategy.AssignmentSlot;
import com.example.tgs_dev.service.strategy.ScheduleInitStrategy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static com.example.tgs_dev.TestFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.clearInvocations;

/**
 * Unit tests for {@link OperationOrchestratorService}.
 *
 * <p>The strategy is injected as a mock — concrete strategy behaviour is
 * tested in {@link com.example.tgs_dev.service.strategy.RotationBasedStrategyTest}.
 * These tests verify that the orchestrator correctly wires the pipeline and
 * dispatches to the right strategy based on the company's scheduling mode.
 *
 * <p>The subject under test ({@code sut}) is constructed manually because the
 * constructor accepts {@code List<ScheduleInitStrategy>}, which Mockito's
 * {@code @InjectMocks} does not handle.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OperationOrchestratorService")
class OperationOrchestratorServiceTest {

    @Mock RouteOperationService    routeOperationService;
    @Mock VehicleAssignmentService vehicleAssignmentService;
    @Mock ScheduleService          scheduleService;
    @Mock RouteService             routeService;
    @Mock TenantService            tenantService;
    @Mock ScheduleInitStrategy     strategy;

    OperationOrchestratorService sut;

    private Route            route1;
    private Route            route2;
    private RotationEntry    entry1;
    private Company          company;

    @BeforeEach
    void setUp() {
        route1  = route(1, "1");
        route2  = route(2, "2");
        entry1  = entry(vehicle(10, "V-001"), template(100, route1, LocalTime.of(6, 0)));
        company = company(1, "Test Corp", SchedulingMode.ROTATION_BASED);

        // strategy self-identifies as ROTATION_BASED so the orchestrator map is populated
        lenient().when(strategy.mode()).thenReturn(SchedulingMode.ROTATION_BASED);
        lenient().when(tenantService.currentCompany()).thenReturn(company);

        sut = new OperationOrchestratorService(
                routeOperationService, vehicleAssignmentService, scheduleService,
                routeService, tenantService, List.of(strategy));

        // strategy.mode() is called during construction to populate the dispatch map.
        // Clear the interaction log so individual tests start from a clean slate.
        clearInvocations(strategy);
    }

    // ── initOperation ─────────────────────────────────────────────────────────

    @Nested @DisplayName("initOperation")
    class InitOperation {

        private static final LocalDate DATE = LocalDate.of(2024, 1, 15);

        @Test @DisplayName("resolves slots via strategy, creates operation, assigns vehicles, calculates schedules — in order")
        void invokesStagesInOrder() {
            RouteOperation          op          = operation(1, route1, DATE);
            List<VehicleAssignment> assignments = List.of(new VehicleAssignment());

            List<AssignmentSlot> slots = List.<AssignmentSlot>of(entry1);
            when(strategy.resolve(route1, DATE)).thenReturn(slots);
            when(routeOperationService.initRoutOperation(route1, DATE)).thenReturn(op);
            when(vehicleAssignmentService.assignVehicles(slots, op)).thenReturn(assignments);

            sut.initOperation(route1, DATE);

            var ordered = inOrder(strategy, routeOperationService, vehicleAssignmentService, scheduleService);
            ordered.verify(strategy).resolve(route1, DATE);
            ordered.verify(routeOperationService).initRoutOperation(route1, DATE);
            ordered.verify(vehicleAssignmentService).assignVehicles(slots, op);
            ordered.verify(scheduleService).calculateVehicleSchedules(assignments);
        }

        @Test @DisplayName("passes resolved slots directly to the assignment service without modification")
        void passesResolvedSlotsUnmodified() {
            AssignmentSlot         customSlot = slot(vehicle(10, "V-001"), template(100, route1, LocalTime.of(6, 0)));
            List<AssignmentSlot>   slots      = List.of(customSlot);
            RouteOperation         op         = operation(1, route1, DATE);

            when(strategy.resolve(route1, DATE)).thenReturn(slots);
            when(routeOperationService.initRoutOperation(route1, DATE)).thenReturn(op);
            when(vehicleAssignmentService.assignVehicles(slots, op)).thenReturn(List.of());

            sut.initOperation(route1, DATE);

            verify(vehicleAssignmentService).assignVehicles(slots, op);
        }
    }

    // ── initAllOperations ─────────────────────────────────────────────────────

    @Nested @DisplayName("initAllOperations")
    class InitAllOperations {

        private static final LocalDate DATE = LocalDate.of(2024, 1, 15);

        @Test @DisplayName("initialises one operation per route returned by routeService")
        void oneOperationPerRoute() {
            when(routeService.findAll()).thenReturn(List.of(route1, route2));
            when(strategy.resolve(any(), eq(DATE))).thenReturn(List.of());
            when(routeOperationService.initRoutOperation(any(), any()))
                    .thenReturn(operation(1, route1, DATE));
            when(vehicleAssignmentService.assignVehicles(any(), any())).thenReturn(List.of());

            sut.initAllOperations(DATE);

            verify(routeOperationService).initRoutOperation(route1, DATE);
            verify(routeOperationService).initRoutOperation(route2, DATE);
        }

        @Test @DisplayName("strategy.resolve is called once per route")
        void strategyCalledOncePerRoute() {
            when(routeService.findAll()).thenReturn(List.of(route1, route2));
            when(strategy.resolve(any(), eq(DATE))).thenReturn(List.of());
            when(routeOperationService.initRoutOperation(any(), any()))
                    .thenReturn(operation(1, route1, DATE));
            when(vehicleAssignmentService.assignVehicles(any(), any())).thenReturn(List.of());

            sut.initAllOperations(DATE);

            verify(strategy).resolve(route1, DATE);
            verify(strategy).resolve(route2, DATE);
            verifyNoMoreInteractions(strategy);
        }

        @Test @DisplayName("empty slot list from strategy results in an empty assignment call")
        void emptySlots_propagatedToAssignmentService() {
            when(routeService.findAll()).thenReturn(List.of(route1));
            when(strategy.resolve(route1, DATE)).thenReturn(List.of());
            when(routeOperationService.initRoutOperation(route1, DATE))
                    .thenReturn(operation(1, route1, DATE));
            when(vehicleAssignmentService.assignVehicles(any(), any())).thenReturn(List.of());

            sut.initAllOperations(DATE);

            verify(vehicleAssignmentService).assignVehicles(eq(List.of()), any());
        }
    }

    // ── Strategy dispatch ─────────────────────────────────────────────────────

    @Nested @DisplayName("strategy dispatch")
    class StrategyDispatch {

        private static final LocalDate DATE = LocalDate.of(2024, 1, 15);

        @Test @DisplayName("dispatches to the strategy matching the company's scheduling mode")
        void dispatchesByCompanyMode() {
            when(strategy.resolve(route1, DATE)).thenReturn(List.of());
            when(routeOperationService.initRoutOperation(route1, DATE))
                    .thenReturn(operation(1, route1, DATE));
            when(vehicleAssignmentService.assignVehicles(any(), any())).thenReturn(List.of());

            sut.initOperation(route1, DATE);

            verify(strategy).resolve(route1, DATE);
        }

        @Test @DisplayName("throws IllegalStateException when no strategy is registered for the company's mode")
        void throwsWhenStrategyMissing() {
            // Build an orchestrator with NO registered strategies
            OperationOrchestratorService empty = new OperationOrchestratorService(
                    routeOperationService, vehicleAssignmentService, scheduleService,
                    routeService, tenantService, List.of());

            assertThatThrownBy(() -> empty.initOperation(route1, DATE))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("ROTATION_BASED")
                    .hasMessageContaining("ScheduleInitStrategy");
        }

        @Test @DisplayName("only the strategy whose mode() matches the company is invoked")
        void onlyMatchingStrategyInvoked() {
            // Build a fresh orchestrator with an isolated strategy mock
            ScheduleInitStrategy isolated = mock(ScheduleInitStrategy.class);
            when(isolated.mode()).thenReturn(SchedulingMode.ROTATION_BASED);
            when(isolated.resolve(route1, DATE)).thenReturn(List.of());

            OperationOrchestratorService sut2 = new OperationOrchestratorService(
                    routeOperationService, vehicleAssignmentService, scheduleService,
                    routeService, tenantService, List.of(isolated));

            when(routeOperationService.initRoutOperation(route1, DATE))
                    .thenReturn(operation(1, route1, DATE));
            when(vehicleAssignmentService.assignVehicles(any(), any())).thenReturn(List.of());

            sut2.initOperation(route1, DATE);

            // 'isolated' is the only registered strategy, so it must be invoked
            verify(isolated).resolve(route1, DATE);
            // 'strategy' from setUp() is NOT wired into sut2, so it must not be touched
            verifyNoInteractions(strategy);
        }
    }
}
