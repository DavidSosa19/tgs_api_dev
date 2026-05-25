package com.example.tgs_dev.service.strategy;

import com.example.tgs_dev.entity.RotationEntry;
import com.example.tgs_dev.entity.Route;
import com.example.tgs_dev.entity.enums.SchedulingMode;
import com.example.tgs_dev.entity.enums.ShiftDayType;
import com.example.tgs_dev.service.VehicleRotationService;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RotationBasedStrategy")
class RotationBasedStrategyTest {

    @Mock VehicleRotationService vehicleRotationService;

    @InjectMocks RotationBasedStrategy sut;

    private Route         route1;
    private Route         route2;
    private RotationEntry entry1;
    private RotationEntry entry2;

    @BeforeEach
    void setUp() {
        route1 = route(1, "1");
        route2 = route(2, "2");
        entry1 = entry(vehicle(10, "V-001"), template(100, route1, LocalTime.of(6, 0)));
        entry2 = entry(vehicle(20, "V-002"), template(200, route2, LocalTime.of(7, 0)));
    }

    // ── mode() ───────────────────────────────────────────────────────────────

    @Test @DisplayName("mode() returns ROTATION_BASED")
    void modeIsRotationBased() {
        assertThat(sut.mode()).isEqualTo(SchedulingMode.ROTATION_BASED);
    }

    // ── groupByRoute (pure function) ─────────────────────────────────────────

    @Nested @DisplayName("groupByRoute (pure function)")
    class GroupByRoute {

        @Test @DisplayName("groups entries by their template's route number")
        void groupsByRouteNumber() {
            Map<String, List<RotationEntry>> result = sut.groupByRoute(List.of(entry1, entry2));

            assertThat(result).containsOnlyKeys("1", "2");
            assertThat(result.get("1")).containsExactly(entry1);
            assertThat(result.get("2")).containsExactly(entry2);
        }

        @Test @DisplayName("multiple entries for the same route are all grouped together")
        void multipleEntriesSameRoute() {
            RotationEntry extra = entry(vehicle(30, "V-003"), template(101, route1, LocalTime.of(8, 0)));

            Map<String, List<RotationEntry>> result = sut.groupByRoute(List.of(entry1, extra));

            assertThat(result.get("1")).containsExactly(entry1, extra);
        }

        @Test @DisplayName("empty input returns an empty map")
        void emptyInput() {
            assertThat(sut.groupByRoute(List.of())).isEmpty();
        }
    }

    // ── resolve ──────────────────────────────────────────────────────────────

    @Nested @DisplayName("resolve")
    class Resolve {

        private static final LocalDate DATE = LocalDate.of(2024, 1, 15);

        @Test @DisplayName("returns only the entries matching the requested route")
        void filtersToRequestedRoute() {
            try (MockedStatic<DateUtils> utils = mockStatic(DateUtils.class)) {
                utils.when(() -> DateUtils.getTypeofDay(DATE)).thenReturn(ShiftDayType.BUSINESS_DAYS);
                when(vehicleRotationService.getRotationFromDate(ShiftDayType.BUSINESS_DAYS, DATE))
                        .thenReturn(List.of(entry1, entry2));  // two routes in the rotation

                List<AssignmentSlot> result = sut.resolve(route1, DATE);

                assertThat(result).containsExactly(entry1);
            }
        }

        @Test @DisplayName("returns empty list when the route has no entries in the rotation")
        void returnsEmptyWhenRouteNotInRotation() {
            Route routeX = route(99, "99");
            try (MockedStatic<DateUtils> utils = mockStatic(DateUtils.class)) {
                utils.when(() -> DateUtils.getTypeofDay(DATE)).thenReturn(ShiftDayType.BUSINESS_DAYS);
                when(vehicleRotationService.getRotationFromDate(ShiftDayType.BUSINESS_DAYS, DATE))
                        .thenReturn(List.of(entry1, entry2));

                List<AssignmentSlot> result = sut.resolve(routeX, DATE);

                assertThat(result).isEmpty();
            }
        }

        @Test @DisplayName("passes the correct ShiftDayType to the rotation service")
        void passesCorrectDayType() {
            LocalDate holiday = LocalDate.of(2024, 1, 1);
            try (MockedStatic<DateUtils> utils = mockStatic(DateUtils.class)) {
                utils.when(() -> DateUtils.getTypeofDay(holiday)).thenReturn(ShiftDayType.HOLIDAYS);
                when(vehicleRotationService.getRotationFromDate(ShiftDayType.HOLIDAYS, holiday))
                        .thenReturn(List.of());

                sut.resolve(route1, holiday);

                verify(vehicleRotationService).getRotationFromDate(ShiftDayType.HOLIDAYS, holiday);
            }
        }

        @Test @DisplayName("returned slots satisfy the AssignmentSlot contract (vehicle + template non-null)")
        void slotsExposeCorrectData() {
            try (MockedStatic<DateUtils> utils = mockStatic(DateUtils.class)) {
                utils.when(() -> DateUtils.getTypeofDay(DATE)).thenReturn(ShiftDayType.BUSINESS_DAYS);
                when(vehicleRotationService.getRotationFromDate(ShiftDayType.BUSINESS_DAYS, DATE))
                        .thenReturn(List.of(entry1));

                List<? extends AssignmentSlot> slots = sut.resolve(route1, DATE);

                assertThat(slots).hasSize(1);
                assertThat(slots.get(0).getVehicle()).isEqualTo(entry1.getVehicle());
                assertThat(slots.get(0).getScheduleTemplate()).isEqualTo(entry1.getScheduleTemplate());
            }
        }
    }
}
