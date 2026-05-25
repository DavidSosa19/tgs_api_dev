package com.example.tgs_dev.service.schedule;

import com.example.tgs_dev.entity.Route;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static com.example.tgs_dev.TestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link TimeRangeDurationResolver}.
 *
 * The {@code next} resolver is a mock; this test verifies only the
 * routing/delegation logic of {@link TimeRangeDurationResolver} itself.
 */
@DisplayName("TimeRangeDurationResolver")
class TimeRangeDurationResolverTest {

    private final DurationResolver       next = mock(DurationResolver.class);
    private final TimeRangeDurationResolver sut  = new TimeRangeDurationResolver(next);

    private static final LocalDate  DATE  = LocalDate.of(2024, 6, 15);
    private static final Route      ROUTE = route(1, "R1");

    // ── Delegation when no ranges ─────────────────────────────────────────────

    @Nested @DisplayName("empty effectiveTimeRanges → always delegates")
    class NoRanges {
        @Test @DisplayName("delegates when effectiveTimeRanges is empty")
        void delegates_whenEmpty() {
            when(next.resolve(any())).thenReturn(30);

            assertThat(sut.resolve(ctx(LocalTime.of(8, 0), List.of()))).isEqualTo(30);
            verify(next).resolve(any());
        }
    }

    // ── Range matching ────────────────────────────────────────────────────────

    @Nested @DisplayName("with time ranges")
    class RangeMatching {

        private List<TimeRangeLookup> peakAndOffPeak() {
            return List.of(
                    lookup(LocalTime.of(6, 0),  LocalTime.of(9, 0),  120),
                    lookup(LocalTime.of(9, 0),  LocalTime.of(18, 0),  60)
            );
        }

        @Test @DisplayName("returns peak duration when time is in peak range")
        void peakDuration() {
            assertThat(sut.resolve(ctx(LocalTime.of(7, 0), peakAndOffPeak())))
                    .isEqualTo(120);
            verifyNoInteractions(next);
        }

        @Test @DisplayName("returns off-peak duration when time is in off-peak range")
        void offPeakDuration() {
            assertThat(sut.resolve(ctx(LocalTime.of(12, 0), peakAndOffPeak())))
                    .isEqualTo(60);
            verifyNoInteractions(next);
        }

        @Test @DisplayName("delegates to next when time falls in a gap between ranges")
        void gap_delegatesToNext() {
            List<TimeRangeLookup> ranges = List.of(
                    lookup(LocalTime.of(6, 0), LocalTime.of(8, 0), 120),
                    lookup(LocalTime.of(9, 0), LocalTime.of(18, 0), 60));
            when(next.resolve(any())).thenReturn(30);

            // 08:30 is in the gap
            assertThat(sut.resolve(ctx(LocalTime.of(8, 30), ranges))).isEqualTo(30);
            verify(next).resolve(any());
        }

        @Test @DisplayName("delegates to next when time is before all ranges")
        void beforeAllRanges_delegatesToNext() {
            when(next.resolve(any())).thenReturn(30);
            assertThat(sut.resolve(ctx(LocalTime.of(5, 0), peakAndOffPeak())))
                    .isEqualTo(30);
            verify(next).resolve(any());
        }

        @Test @DisplayName("delegates to next when time is after all ranges")
        void afterAllRanges_delegatesToNext() {
            when(next.resolve(any())).thenReturn(30);
            assertThat(sut.resolve(ctx(LocalTime.of(20, 0), peakAndOffPeak())))
                    .isEqualTo(30);
            verify(next).resolve(any());
        }

        @Test @DisplayName("boundary: exactly at range end → next range")
        void exactlyAtRangeEnd_secondRange() {
            // 09:00 is the exclusive end of peak, start of off-peak
            assertThat(sut.resolve(ctx(LocalTime.of(9, 0), peakAndOffPeak())))
                    .isEqualTo(60);
            verifyNoInteractions(next);
        }
    }

    // ── Sequence simulation ───────────────────────────────────────────────────

    @Test @DisplayName("simulates 7-cycle schedule crossing from peak to off-peak")
    void sevenCyclesAcrossRanges() {
        // peak 06:00–09:00 (120 min), off-peak 09:00–18:00 (60 min)
        // Start 06:30, 7 cycles
        // Expected departure times:
        //   06:30 (peak 120), 08:30 (peak 120), 10:30 (off-peak 60),
        //   11:30, 12:30, 13:30, 14:30
        List<TimeRangeLookup> ranges = List.of(
                lookup(LocalTime.of(6, 0),  LocalTime.of(9,  0), 120),
                lookup(LocalTime.of(9, 0),  LocalTime.of(18, 0),  60));
        LocalTime time = LocalTime.of(6, 30);

        LocalTime[] expected = {
            LocalTime.of(6, 30),
            LocalTime.of(8, 30),
            LocalTime.of(10, 30),
            LocalTime.of(11, 30),
            LocalTime.of(12, 30),
            LocalTime.of(13, 30),
            LocalTime.of(14, 30)
        };

        for (int i = 0; i < 7; i++) {
            int duration = sut.resolve(ctx(time, ranges));
            assertThat(time).as("departure[%d]", i).isEqualTo(expected[i]);
            time = time.plusMinutes(duration);
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private DurationResolverContext ctx(LocalTime time, List<TimeRangeLookup> ranges) {
        return new DurationResolverContext(ROUTE, time, DATE, 30, ranges);
    }
}
