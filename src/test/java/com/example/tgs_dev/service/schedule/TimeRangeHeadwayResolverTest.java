package com.example.tgs_dev.service.schedule;

import com.example.tgs_dev.entity.Route;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static com.example.tgs_dev.TestFixtures.route;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link TimeRangeHeadwayResolver}.
 *
 * <p>Mirrors {@link TimeRangeDurationResolverTest} — same delegation structure,
 * different chain (HeadwayResolver instead of DurationResolver).
 */
@DisplayName("TimeRangeHeadwayResolver")
class TimeRangeHeadwayResolverTest {

    private final HeadwayResolver            next = mock(HeadwayResolver.class);
    private final TimeRangeHeadwayResolver   sut  = new TimeRangeHeadwayResolver(next);

    private static final LocalDate DATE  = LocalDate.of(2024, 6, 15);
    private static final Route     ROUTE = route(1, "R1");

    // ── Empty ranges → always delegates ──────────────────────────────────────

    @Nested @DisplayName("empty effectiveTimeRanges → delegates to next")
    class NoRanges {

        @Test @DisplayName("delegates when effectiveTimeRanges is empty")
        void delegates_whenEmpty() {
            when(next.resolve(any())).thenReturn(8);

            assertThat(sut.resolve(ctx(LocalTime.of(8, 0), List.of()))).isEqualTo(8);
            verify(next).resolve(any());
        }
    }

    // ── Range matching ────────────────────────────────────────────────────────

    @Nested @DisplayName("with time ranges")
    class RangeMatching {

        private List<TimeRangeLookup> peakAndOffPeak() {
            return List.of(
                    new TimeRangeLookup(LocalTime.of(6, 0),  LocalTime.of(9,  0), 90, 5, false),
                    new TimeRangeLookup(LocalTime.of(9, 0),  LocalTime.of(18, 0), 60, 10, false)
            );
        }

        @Test @DisplayName("returns peak headway when time is in peak range")
        void peakHeadway() {
            assertThat(sut.resolve(ctx(LocalTime.of(7, 0), peakAndOffPeak()))).isEqualTo(5);
            verifyNoInteractions(next);
        }

        @Test @DisplayName("returns off-peak headway when time is in off-peak range")
        void offPeakHeadway() {
            assertThat(sut.resolve(ctx(LocalTime.of(12, 0), peakAndOffPeak()))).isEqualTo(10);
            verifyNoInteractions(next);
        }

        @Test @DisplayName("delegates to next when time falls in a gap between ranges")
        void gap_delegatesToNext() {
            List<TimeRangeLookup> ranges = List.of(
                    new TimeRangeLookup(LocalTime.of(6, 0), LocalTime.of(8, 0), 90, 5, false),
                    new TimeRangeLookup(LocalTime.of(9, 0), LocalTime.of(18, 0), 60, 10, false));
            when(next.resolve(any())).thenReturn(8);

            assertThat(sut.resolve(ctx(LocalTime.of(8, 30), ranges))).isEqualTo(8);
            verify(next).resolve(any());
        }

        @Test @DisplayName("delegates to next when time is before all ranges")
        void beforeAllRanges_delegatesToNext() {
            when(next.resolve(any())).thenReturn(8);
            assertThat(sut.resolve(ctx(LocalTime.of(5, 0), peakAndOffPeak()))).isEqualTo(8);
            verify(next).resolve(any());
        }

        @Test @DisplayName("delegates to next when time is after all ranges")
        void afterAllRanges_delegatesToNext() {
            when(next.resolve(any())).thenReturn(8);
            assertThat(sut.resolve(ctx(LocalTime.of(20, 0), peakAndOffPeak()))).isEqualTo(8);
            verify(next).resolve(any());
        }

        @Test @DisplayName("delegates to next when range has headwayMinutes = 0")
        void zeroHeadwayRange_delegatesToNext() {
            // A range without headway data (e.g. duration-only time range)
            List<TimeRangeLookup> ranges = List.of(
                    new TimeRangeLookup(LocalTime.of(6, 0), LocalTime.of(20, 0), 60, 0, false));
            when(next.resolve(any())).thenReturn(8);

            assertThat(sut.resolve(ctx(LocalTime.of(10, 0), ranges))).isEqualTo(8);
            verify(next).resolve(any());
        }

        @Test @DisplayName("boundary: exactly at range end → matches next range")
        void exactlyAtRangeEnd_matchesNextRange() {
            assertThat(sut.resolve(ctx(LocalTime.of(9, 0), peakAndOffPeak()))).isEqualTo(10);
            verifyNoInteractions(next);
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private ScheduleResolverContext ctx(LocalTime time, List<TimeRangeLookup> ranges) {
        return new ScheduleResolverContext(ROUTE, time, DATE, 60, 8, ranges);
    }
}
