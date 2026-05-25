package com.example.tgs_dev.service.schedule;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalTime;
import java.util.List;

import static com.example.tgs_dev.TestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests for {@link RouteTimeRangeResolver}.
 *
 * No mocks, no Spring context.  All edge cases from the design document are covered.
 */
@DisplayName("RouteTimeRangeResolver")
class RouteTimeRangeResolverTest {

    // Shared fixture: three consecutive non-overlapping ranges
    // [06:00, 09:00) → 120 min   (peak)
    // [09:00, 18:00) → 60  min   (off-peak)
    // [18:00, 22:00) → 90  min   (evening)
    private static final List<TimeRangeLookup> THREE_RANGES = List.of(
            lookup(LocalTime.of(6,  0), LocalTime.of(9,  0), 120),
            lookup(LocalTime.of(9,  0), LocalTime.of(18, 0),  60),
            lookup(LocalTime.of(18, 0), LocalTime.of(22, 0),  90)
    );

    // ── Empty / null input ─────────────────────────────────────────────────────

    @Nested @DisplayName("empty or null input")
    class EmptyInput {
        @Test @DisplayName("null list → empty")
        void nullList_returnsEmpty() {
            assertThat(RouteTimeRangeResolver.resolve(LocalTime.of(8, 0), null)).isEmpty();
        }

        @Test @DisplayName("empty list → empty")
        void emptyList_returnsEmpty() {
            assertThat(RouteTimeRangeResolver.resolve(LocalTime.of(8, 0), List.of())).isEmpty();
        }
    }

    // ── Happy path: matches ────────────────────────────────────────────────────

    @Nested @DisplayName("matching ranges")
    class Matching {
        @Test @DisplayName("time in first range → first range duration")
        void firstRange() {
            assertThat(RouteTimeRangeResolver.resolve(LocalTime.of(7, 0), THREE_RANGES))
                    .hasValue(120);
        }

        @Test @DisplayName("time in middle range → middle range duration")
        void middleRange() {
            assertThat(RouteTimeRangeResolver.resolve(LocalTime.of(12, 0), THREE_RANGES))
                    .hasValue(60);
        }

        @Test @DisplayName("time in last range → last range duration")
        void lastRange() {
            assertThat(RouteTimeRangeResolver.resolve(LocalTime.of(20, 0), THREE_RANGES))
                    .hasValue(90);
        }
    }

    // ── Boundary semantics: [start, end) ──────────────────────────────────────

    @Nested @DisplayName("boundary semantics — [start, end)")
    class Boundaries {
        @Test @DisplayName("exactly at rangeStart is inclusive → matches")
        void exactlyAtRangeStart_matches() {
            // 06:00 is the start of the first range
            assertThat(RouteTimeRangeResolver.resolve(LocalTime.of(6, 0), THREE_RANGES))
                    .hasValue(120);
        }

        @Test @DisplayName("exactly at rangeEnd is exclusive → falls to next range")
        void exactlyAtRangeEnd_fallsToNextRange() {
            // 09:00 is the END of range-1 and the START of range-2
            assertThat(RouteTimeRangeResolver.resolve(LocalTime.of(9, 0), THREE_RANGES))
                    .hasValue(60);   // ← range-2, not range-1
        }

        @Test @DisplayName("boundary between range-2 and range-3 maps to range-3")
        void boundaryBetweenMiddleAndLast() {
            assertThat(RouteTimeRangeResolver.resolve(LocalTime.of(18, 0), THREE_RANGES))
                    .hasValue(90);
        }

        @ParameterizedTest(name = "time {0} → expected duration {1}")
        @CsvSource({
            "06:00, 120",   // inclusive start
            "08:59, 120",   // just before end of first
            "09:00,  60",   // exactly at boundary: second range
            "17:59,  60",   // just before end of middle
            "18:00,  90",   // exactly at boundary: third range
            "21:59,  90",   // just before end of last
        })
        @DisplayName("parameterised boundary sweep")
        void boundarySweep(String timeStr, int expectedDuration) {
            LocalTime time = LocalTime.parse(timeStr);
            assertThat(RouteTimeRangeResolver.resolve(time, THREE_RANGES))
                    .hasValue(expectedDuration);
        }
    }

    // ── Gaps and out-of-range times ────────────────────────────────────────────

    @Nested @DisplayName("gaps and out-of-range")
    class Gaps {
        @Test @DisplayName("time before all ranges → empty (gap at start of day)")
        void beforeAllRanges() {
            assertThat(RouteTimeRangeResolver.resolve(LocalTime.of(5, 0), THREE_RANGES))
                    .isEmpty();
        }

        @Test @DisplayName("time after all ranges → empty (gap at end of day)")
        void afterAllRanges() {
            // 22:00 is the exclusive end of the last range
            assertThat(RouteTimeRangeResolver.resolve(LocalTime.of(22, 0), THREE_RANGES))
                    .isEmpty();
        }

        @Test @DisplayName("time in deliberate gap between two ranges → empty")
        void inGap() {
            // Ranges with a gap from 09:00 to 10:00
            List<TimeRangeLookup> gapped = List.of(
                    lookup(LocalTime.of(6, 0),  LocalTime.of(9, 0),  120),
                    lookup(LocalTime.of(10, 0), LocalTime.of(18, 0),  60)
            );
            // 09:30 is in the gap
            assertThat(RouteTimeRangeResolver.resolve(LocalTime.of(9, 30), gapped))
                    .isEmpty();
        }

        @Test @DisplayName("exactly at gap start (exclusive end of range A) → empty")
        void exactlyAtGapStart() {
            List<TimeRangeLookup> gapped = List.of(
                    lookup(LocalTime.of(6, 0), LocalTime.of(8, 0), 120),
                    lookup(LocalTime.of(9, 0), LocalTime.of(18, 0), 60)
            );
            // 08:00 is exclusive end of range-1; 09:00 is start of range-2 — so 08:00 is in the gap
            assertThat(RouteTimeRangeResolver.resolve(LocalTime.of(8, 0), gapped))
                    .isEmpty();
        }
    }

    // ── Phase 2: overnight ranges ─────────────────────────────────────────────

    @Nested @DisplayName("overnight ranges (crossesMidnight = true)")
    class Overnight {
        private static final TimeRangeLookup OVERNIGHT = overnightLookup(
                LocalTime.of(22, 0), LocalTime.of(2, 0), 45);

        @Test @DisplayName("time after rangeStart (same day) → matches overnight range")
        void afterStartSameDay() {
            assertThat(RouteTimeRangeResolver.resolve(LocalTime.of(23, 0), List.of(OVERNIGHT)))
                    .hasValue(45);
        }

        @Test @DisplayName("time before rangeEnd (next day) → matches overnight range")
        void beforeEndNextDay() {
            assertThat(RouteTimeRangeResolver.resolve(LocalTime.of(1, 0), List.of(OVERNIGHT)))
                    .hasValue(45);
        }

        @Test @DisplayName("exactly at overnight rangeStart → matches (inclusive)")
        void exactlyAtOvernightStart() {
            assertThat(RouteTimeRangeResolver.resolve(LocalTime.of(22, 0), List.of(OVERNIGHT)))
                    .hasValue(45);
        }

        @Test @DisplayName("exactly at overnight rangeEnd → does not match (exclusive)")
        void exactlyAtOvernightEnd() {
            assertThat(RouteTimeRangeResolver.resolve(LocalTime.of(2, 0), List.of(OVERNIGHT)))
                    .isEmpty();
        }

        @Test @DisplayName("time between overnight end and start → does not match")
        void middleOfDayOutsideOvernight() {
            assertThat(RouteTimeRangeResolver.resolve(LocalTime.of(12, 0), List.of(OVERNIGHT)))
                    .isEmpty();
        }

        @Test @DisplayName("midnight (00:00) is inside overnight range")
        void midnight() {
            assertThat(RouteTimeRangeResolver.resolve(LocalTime.MIDNIGHT, List.of(OVERNIGHT)))
                    .hasValue(45);
        }
    }

    // ── matches() helper — direct tests ──────────────────────────────────────

    @Nested @DisplayName("matches() predicate")
    class MatchesPredicate {
        @Test @DisplayName("non-overnight: returns false when time == rangeEnd")
        void nonOvernight_endIsExclusive() {
            TimeRangeLookup range = lookup(LocalTime.of(8, 0), LocalTime.of(9, 0), 30);
            assertThat(RouteTimeRangeResolver.matches(range, LocalTime.of(9, 0))).isFalse();
        }

        @Test @DisplayName("non-overnight: returns true when time == rangeStart")
        void nonOvernight_startIsInclusive() {
            TimeRangeLookup range = lookup(LocalTime.of(8, 0), LocalTime.of(9, 0), 30);
            assertThat(RouteTimeRangeResolver.matches(range, LocalTime.of(8, 0))).isTrue();
        }

        @Test @DisplayName("overnight: 23:59 is inside [22:00, 02:00)")
        void overnight_lateBoundary() {
            TimeRangeLookup range = overnightLookup(LocalTime.of(22, 0), LocalTime.of(2, 0), 30);
            assertThat(RouteTimeRangeResolver.matches(range, LocalTime.of(23, 59))).isTrue();
        }

        @Test @DisplayName("overnight: 02:00 is NOT inside [22:00, 02:00)")
        void overnight_endExclusive() {
            TimeRangeLookup range = overnightLookup(LocalTime.of(22, 0), LocalTime.of(2, 0), 30);
            assertThat(RouteTimeRangeResolver.matches(range, LocalTime.of(2, 0))).isFalse();
        }
    }

    // ── Single range edge case ─────────────────────────────────────────────────

    @Test @DisplayName("single-entry list: returns its duration when time matches")
    void singleRange_matches() {
        List<TimeRangeLookup> single = List.of(
                lookup(LocalTime.of(6, 0), LocalTime.of(22, 0), 30));
        assertThat(RouteTimeRangeResolver.resolve(LocalTime.of(10, 0), single))
                .hasValue(30);
    }

    @Test @DisplayName("single-entry list: returns empty when time is outside")
    void singleRange_noMatch() {
        List<TimeRangeLookup> single = List.of(
                lookup(LocalTime.of(6, 0), LocalTime.of(22, 0), 30));
        assertThat(RouteTimeRangeResolver.resolve(LocalTime.of(23, 0), single))
                .isEmpty();
    }
}
