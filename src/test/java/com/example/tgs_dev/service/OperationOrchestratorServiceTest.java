package com.example.tgs_dev.service;

import com.example.tgs_dev.entity.*;
import com.example.tgs_dev.entity.enums.ShiftDayType;
import com.example.tgs_dev.util.DateUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static com.example.tgs_dev.TestFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OperationOrchestratorService")
class OperationOrchestratorServiceTest {

    @Mock RouteOperationService    routeOperationService;
    @Mock VehicleRotationService   vehicleRotationService;
    @Mock VehicleAssignmentService vehicleAssignmentService;
    @Mock ScheduleService          scheduleService;
    @Mock RouteService             routeService;

    @InjectMocks OperationOrchestratorService sut;

    private Route            route1;
    private Route            route2;
    private ScheduleTemplate template1;
    private ScheduleTemplate template2;
    private RotationEntry    entry1;
    private RotationEntry    entry2;

    @BeforeEach
    void setUp() {
        route1    = route(1, "1");
        route2    = route(2, "2");
        template1 = template(100, route1, LocalTime.of(6, 0));
        template2 = template(200, route2, LocalTime.of(7, 0));
        entry1    = entry(vehicle(10, "V-001"), template1);
        entry2    = entry(vehicle(20, "V-002"), template2);
    }

    // ── getRotationsByRoute ───────────────────────────────────────────────────
    // Pure function — no mocks needed. This is the ideal unit test.
    @Nested @DisplayName("getRotationsByRoute (pure function)")
    class GetRotationsByRoute {

        @Test @DisplayName("groups entries by their template's route number")
        void groupsByRouteNumber() {
            Map<String, List<RotationEntry>> result = sut.getRotationsByRoute(List.of(entry1, entry2));

            assertThat(result).containsOnlyKeys("1", "2");
            assertThat(result.get("1")).containsExactly(entry1);
            assertThat(result.get("2")).containsExactly(entry2);
        }

        @Test @DisplayName("multiple entries for the same route are all grouped together")
        void multipleEntriesSameRoute() {
            RotationEntry extra = entry(vehicle(30, "V-003"), template1);

            Map<String, List<RotationEntry>> result = sut.getRotationsByRoute(List.of(entry1, extra));

            assertThat(result.get("1")).containsExactly(entry1, extra);
        }

        @Test @DisplayName("empty input returns an empty map")
        void emptyInput() {
            assertThat(sut.getRotationsByRoute(List.of())).isEmpty();
        }
    }

    // ── initOperation ─────────────────────────────────────────────────────────
    // initDailyOperation is private — its orchestration logic is verified here
    // through the public initOperation entry point.
    @Nested @DisplayName("initOperation")
    class InitOperation {

        @Test @DisplayName("creates route operation, then assigns vehicles, then calculates schedules")
        void invokesServicesInOrder() {
            LocalDate date = LocalDate.of(2024, 1, 15);
            RouteOperation op = operation(1, route1, date);
            List<VehicleAssignment> assignments = List.of(new VehicleAssignment());

            try (MockedStatic<DateUtils> utils = mockStatic(DateUtils.class)) {
                utils.when(() -> DateUtils.getTypeofDay(date)).thenReturn(ShiftDayType.BUSINESS_DAYS);
                when(vehicleRotationService.getRotationFromDate(ShiftDayType.BUSINESS_DAYS, date))
                        .thenReturn(List.of(entry1));
                when(routeOperationService.initRoutOperation(route1, date)).thenReturn(op);
                when(vehicleAssignmentService.assignVehicles(List.of(entry1), op)).thenReturn(assignments);

                sut.initOperation(route1, date);

                var ordered = inOrder(routeOperationService, vehicleAssignmentService, scheduleService);
                ordered.verify(routeOperationService).initRoutOperation(route1, date);
                ordered.verify(vehicleAssignmentService).assignVehicles(List.of(entry1), op);
                ordered.verify(scheduleService).calculateVehicleSchedules(assignments);
            }
        }
    }

    // ── initAllOperations ─────────────────────────────────────────────────────
    @Nested @DisplayName("initAllOperations")
    class InitAllOperations {

        @Test @DisplayName("routes with no rotation entries receive an empty assignment list")
        void routeWithNoEntries_getsEmptyList() {
            LocalDate date  = LocalDate.of(2024, 1, 15);
            Route routeX    = route(99, "99");  // no entries for this route

            try (MockedStatic<DateUtils> utils = mockStatic(DateUtils.class)) {
                utils.when(() -> DateUtils.getTypeofDay(date)).thenReturn(ShiftDayType.BUSINESS_DAYS);
                when(vehicleRotationService.getRotationFromDate(ShiftDayType.BUSINESS_DAYS, date))
                        .thenReturn(List.of(entry1));          // only entry1 belongs to route1
                when(routeService.findAll()).thenReturn(List.of(route1, routeX));
                when(routeOperationService.initRoutOperation(any(), eq(date)))
                        .thenReturn(operation(1, route1, date));
                when(vehicleAssignmentService.assignVehicles(any(), any())).thenReturn(List.of());

                sut.initAllOperations(date);

                verify(vehicleAssignmentService).assignVehicles(eq(List.of(entry1)), any());
                verify(vehicleAssignmentService).assignVehicles(eq(List.of()), any());
            }
        }

        @Test @DisplayName("one operation is initialized per route")
        void oneOperationPerRoute() {
            LocalDate date = LocalDate.of(2024, 1, 15);
            try (MockedStatic<DateUtils> utils = mockStatic(DateUtils.class)) {
                utils.when(() -> DateUtils.getTypeofDay(date)).thenReturn(ShiftDayType.BUSINESS_DAYS);
                when(vehicleRotationService.getRotationFromDate(any(), eq(date)))
                        .thenReturn(List.of(entry1, entry2));
                when(routeService.findAll()).thenReturn(List.of(route1, route2));
                when(routeOperationService.initRoutOperation(any(), any()))
                        .thenReturn(operation(1, route1, date));
                when(vehicleAssignmentService.assignVehicles(any(), any())).thenReturn(List.of());

                sut.initAllOperations(date);

                verify(routeOperationService, times(2)).initRoutOperation(any(), eq(date));
            }
        }
    }
}
