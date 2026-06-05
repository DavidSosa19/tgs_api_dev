package com.example.tgs_dev.service.schedule;

import com.example.tgs_dev.entity.Route;
import com.example.tgs_dev.entity.RouteCalendarOverride;
import com.example.tgs_dev.entity.RouteCalendarOverrideRange;
import com.example.tgs_dev.service.RouteCalendarOverrideService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.example.tgs_dev.TestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CalendarOverrideDurationResolver")
class CalendarOverrideDurationResolverTest {

    @Mock RouteCalendarOverrideService overrideService;
    @Mock DurationResolver             next;

    CalendarOverrideDurationResolver sut;

    private static final LocalDate  DATE  = LocalDate.of(2024, 1, 15);
    private static final Route      ROUTE = route(1, "R1");

    @BeforeEach
    void setUp() {
        sut = new CalendarOverrideDurationResolver(overrideService, next);
    }

    // ── No override ────────────────────────────────────────────────────────────

    @Test @DisplayName("delegates to next when no override exists for the date")
    void noOverride_delegatesToNext() {
        when(overrideService.findByRouteAndDate(ROUTE, DATE)).thenReturn(Optional.empty());
        when(next.resolve(any())).thenReturn(30);

        assertThat(sut.resolve(ctx(LocalTime.of(8, 0)))).isEqualTo(30);
        verify(next).resolve(any());
    }

    // ── Fixed override ─────────────────────────────────────────────────────────

    @Nested @DisplayName("override with useTimeRanges = false (fixed)")
    class FixedOverride {
        @Test @DisplayName("returns override baseDuration regardless of departure time")
        void returnsBaseDuration() {
            RouteCalendarOverride ov = fixedOverride(90);
            when(overrideService.findByRouteAndDate(ROUTE, DATE)).thenReturn(Optional.of(ov));

            assertThat(sut.resolve(ctx(LocalTime.of(6,  0)))).isEqualTo(90);
            assertThat(sut.resolve(ctx(LocalTime.of(14, 0)))).isEqualTo(90);
            verifyNoInteractions(next);
        }
    }

    // ── Range override ─────────────────────────────────────────────────────────

    @Nested @DisplayName("override with useTimeRanges = true")
    class RangeOverride {
        @Test @DisplayName("returns matched range duration when time is inside a range")
        void matchedRange() {
            RouteCalendarOverride ov = rangeOverride(50,
                    overrideRange(LocalTime.of(6, 0), LocalTime.of(9, 0), 120),
                    overrideRange(LocalTime.of(9, 0), LocalTime.of(18, 0), 60));
            when(overrideService.findByRouteAndDate(ROUTE, DATE)).thenReturn(Optional.of(ov));

            assertThat(sut.resolve(ctx(LocalTime.of(7, 0)))).isEqualTo(120);
            assertThat(sut.resolve(ctx(LocalTime.of(12, 0)))).isEqualTo(60);
            verifyNoInteractions(next);
        }

        @Test @DisplayName("falls back to override baseDuration when time is in a gap — NOT to next/route default")
        void gapFallsBackToOverrideBase_notToRouteDefault() {
            // Gap between 09:00 and 10:00
            RouteCalendarOverride ov = rangeOverride(50,
                    overrideRange(LocalTime.of(6, 0),  LocalTime.of(9, 0),  120),
                    overrideRange(LocalTime.of(10, 0), LocalTime.of(18, 0),  60));
            when(overrideService.findByRouteAndDate(ROUTE, DATE)).thenReturn(Optional.of(ov));

            // 09:30 is in the gap → fallback to override.baseDuration (50), NOT to next
            assertThat(sut.resolve(ctx(LocalTime.of(9, 30)))).isEqualTo(50);
            verifyNoInteractions(next);
        }

        @Test @DisplayName("boundary: exactly at override range end → falls into next range of the override")
        void exactlyAtRangeEnd_nextOverrideRange() {
            RouteCalendarOverride ov = rangeOverride(50,
                    overrideRange(LocalTime.of(6, 0), LocalTime.of(9, 0),  120),
                    overrideRange(LocalTime.of(9, 0), LocalTime.of(18, 0),  60));
            when(overrideService.findByRouteAndDate(ROUTE, DATE)).thenReturn(Optional.of(ov));

            assertThat(sut.resolve(ctx(LocalTime.of(9, 0)))).isEqualTo(60);
        }

        @Test @DisplayName("empty ranges list with useTimeRanges=true → falls back to override baseDuration")
        void emptyRangesWithFlag_usesBaseDuration() {
            RouteCalendarOverride ov = rangeOverride(50); // no ranges
            when(overrideService.findByRouteAndDate(ROUTE, DATE)).thenReturn(Optional.of(ov));

            assertThat(sut.resolve(ctx(LocalTime.of(8, 0)))).isEqualTo(50);
            verifyNoInteractions(next);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ScheduleResolverContext ctx(LocalTime time) {
        return new ScheduleResolverContext(ROUTE, time, DATE, 30, 8, List.of());
    }

    private RouteCalendarOverride fixedOverride(int baseDuration) {
        RouteCalendarOverride ov = new RouteCalendarOverride();
        ov.setUseTimeRanges(false);
        ov.setBaseDuration(baseDuration);
        ov.setRanges(new ArrayList<>());
        return ov;
    }

    private RouteCalendarOverride rangeOverride(int baseDuration,
                                                RouteCalendarOverrideRange... ranges) {
        RouteCalendarOverride ov = new RouteCalendarOverride();
        ov.setUseTimeRanges(true);
        ov.setBaseDuration(baseDuration);
        ov.setRanges(new ArrayList<>(List.of(ranges)));
        return ov;
    }

    private RouteCalendarOverrideRange overrideRange(LocalTime start, LocalTime end, int duration) {
        return new RouteCalendarOverrideRange(start, end, duration, 0, false);
    }
}
