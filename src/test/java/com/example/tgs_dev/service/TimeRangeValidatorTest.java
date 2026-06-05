package com.example.tgs_dev.service;

import com.example.tgs_dev.controller.exception.BusinessException;
import com.example.tgs_dev.controller.request.RouteTimeRangeRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link TimeRangeValidator}.
 *
 * <p>The validator is pure static logic: no Spring context, no mocks.
 * Every branch is exercised directly.
 */
@DisplayName("TimeRangeValidator")
class TimeRangeValidatorTest {

    private static final String CTX = "route";

    // ── Factories ─────────────────────────────────────────────────────────────

    /** Non-overnight range starting at {@code hour:00} with 30-min window. */
    private static RouteTimeRangeRequest range(int startHour) {
        return new RouteTimeRangeRequest(
                LocalTime.of(startHour, 0),
                LocalTime.of(startHour, 30),
                30,
                8,
                false);
    }

    /** Non-overlapping list of N ranges starting at hours 6, 7, 8 … */
    private static List<RouteTimeRangeRequest> nRanges(int n) {
        List<RouteTimeRangeRequest> list = new ArrayList<>();
        for (int i = 0; i < n; i++) list.add(range(6 + i));
        return list;
    }

    private static RouteTimeRangeRequest overnight(int startHour, int endHour) {
        return new RouteTimeRangeRequest(
                LocalTime.of(startHour, 0),
                LocalTime.of(endHour, 0),
                60,
                8,
                true);
    }

    // ── Null / empty ──────────────────────────────────────────────────────────

    @Nested @DisplayName("null or empty list")
    class NullOrEmpty {

        @Test @DisplayName("null list → timeRanges.required")
        void nullList_throws() {
            assertThatThrownBy(() -> TimeRangeValidator.validate(null, CTX))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("timeRanges.required");
        }

        @Test @DisplayName("empty list → timeRanges.required")
        void emptyList_throws() {
            List<RouteTimeRangeRequest> empty = List.of();
            assertThatThrownBy(() -> TimeRangeValidator.validate(empty, CTX))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("timeRanges.required");
        }
    }

    // ── Count validation ──────────────────────────────────────────────────────

    @Nested @DisplayName("count validation")
    class Count {

        @Test @DisplayName("single entry → timeRanges.minTwo")
        void oneEntry_throwsMinTwo() {
            List<RouteTimeRangeRequest> single = List.of(range(6));
            assertThatThrownBy(() -> TimeRangeValidator.validate(single, CTX))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("timeRanges.minTwo");
        }

        @Test @DisplayName("exactly 2 entries → valid")
        void twoEntries_valid() {
            assertThatNoException()
                    .isThrownBy(() -> TimeRangeValidator.validate(nRanges(2), CTX));
        }

        @Test @DisplayName("exactly 10 entries → valid")
        void tenEntries_valid() {
            assertThatNoException()
                    .isThrownBy(() -> TimeRangeValidator.validate(nRanges(10), CTX));
        }

        @Test @DisplayName("11 entries → timeRanges.maxTen")
        void elevenEntries_throwsMaxTen() {
            List<RouteTimeRangeRequest> eleven = nRanges(11);
            assertThatThrownBy(() -> TimeRangeValidator.validate(eleven, CTX))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("timeRanges.maxTen");
        }

        @ParameterizedTest(name = "n={0}")
        @ValueSource(ints = {3, 5, 9})
        @DisplayName("2–10 entries → always valid")
        void midRange_valid(int n) {
            assertThatNoException()
                    .isThrownBy(() -> TimeRangeValidator.validate(nRanges(n), CTX));
        }
    }

    // ── Single-range validation ───────────────────────────────────────────────

    @Nested @DisplayName("end-before-start rule (non-overnight only)")
    class EndBeforeStart {

        @Test @DisplayName("rangeEnd == rangeStart → timeRanges.endBeforeStart")
        void equalTimes_throws() {
            RouteTimeRangeRequest bad = new RouteTimeRangeRequest(
                    LocalTime.of(8, 0), LocalTime.of(8, 0), 30, 8, false);
            List<RouteTimeRangeRequest> twoRanges = List.of(range(6), bad);
            assertThatThrownBy(() -> TimeRangeValidator.validate(twoRanges, CTX))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("timeRanges.endBeforeStart");
        }

        @Test @DisplayName("rangeEnd before rangeStart → timeRanges.endBeforeStart")
        void endBefore_throws() {
            RouteTimeRangeRequest bad = new RouteTimeRangeRequest(
                    LocalTime.of(9, 0), LocalTime.of(8, 0), 30, 8, false);
            List<RouteTimeRangeRequest> twoRanges = List.of(range(6), bad);
            assertThatThrownBy(() -> TimeRangeValidator.validate(twoRanges, CTX))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("timeRanges.endBeforeStart");
        }

        @Test @DisplayName("overnight range with end < start → no error (crossesMidnight=true)")
        void overnight_endBeforeStart_isAllowed() {
            // 22:00 → 02:00 is a valid overnight range
            RouteTimeRangeRequest ov = overnight(22, 2);
            assertThatNoException()
                    .isThrownBy(() -> TimeRangeValidator.validate(List.of(range(6), ov), CTX));
        }
    }

    // ── Overlap detection ─────────────────────────────────────────────────────

    @Nested @DisplayName("overlap detection (non-overnight ranges)")
    class Overlap {

        @Test @DisplayName("non-overlapping ranges → valid")
        void noOverlap_valid() {
            // 06:00–06:30, 07:00–07:30 — gap of 30 min between them
            assertThatNoException()
                    .isThrownBy(() -> TimeRangeValidator.validate(nRanges(2), CTX));
        }

        @Test @DisplayName("touching ranges (end == next start) → valid")
        void touching_valid() {
            // 06:00–07:00, 07:00–08:00 — touching but not overlapping
            RouteTimeRangeRequest r1 = new RouteTimeRangeRequest(
                    LocalTime.of(6, 0), LocalTime.of(7, 0), 30, 8, false);
            RouteTimeRangeRequest r2 = new RouteTimeRangeRequest(
                    LocalTime.of(7, 0), LocalTime.of(8, 0), 30, 8, false);
            assertThatNoException()
                    .isThrownBy(() -> TimeRangeValidator.validate(List.of(r1, r2), CTX));
        }

        @Test @DisplayName("overlapping ranges (end > next start) → timeRanges.overlap")
        void overlap_throws() {
            // 06:00–07:30, 07:00–08:00 — overlaps by 30 min
            RouteTimeRangeRequest r1 = new RouteTimeRangeRequest(
                    LocalTime.of(6, 0), LocalTime.of(7, 30), 30, 8, false);
            RouteTimeRangeRequest r2 = new RouteTimeRangeRequest(
                    LocalTime.of(7, 0), LocalTime.of(8, 0), 30, 8, false);
            List<RouteTimeRangeRequest> ranges = List.of(r1, r2);
            assertThatThrownBy(() -> TimeRangeValidator.validate(ranges, CTX))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("timeRanges.overlap");
        }

        @Test @DisplayName("overlap detected even when input is out of order")
        void overlap_outOfOrder_throws() {
            // Supplied in reverse order — validator sorts before checking
            RouteTimeRangeRequest later   = new RouteTimeRangeRequest(
                    LocalTime.of(7, 0), LocalTime.of(8, 30), 30, 8, false);
            RouteTimeRangeRequest earlier = new RouteTimeRangeRequest(
                    LocalTime.of(6, 0), LocalTime.of(7, 30), 30, 8, false);
            List<RouteTimeRangeRequest> outOfOrder = List.of(later, earlier);
            assertThatThrownBy(() -> TimeRangeValidator.validate(outOfOrder, CTX))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("timeRanges.overlap");
        }

        @Test @DisplayName("overnight ranges are excluded from overlap check")
        void overnightSkippedInOverlapCheck() {
            // Two overnight ranges would "overlap" if checked naively, but the
            // validator explicitly skips them in phase 2.
            RouteTimeRangeRequest ov1 = overnight(22, 2);
            RouteTimeRangeRequest ov2 = overnight(23, 3);
            assertThatNoException()
                    .isThrownBy(() -> TimeRangeValidator.validate(List.of(ov1, ov2), CTX));
        }

        @Test @DisplayName("mix of overnight + regular: regular ranges are still overlap-checked")
        void mixedRanges_regularStillChecked() {
            RouteTimeRangeRequest r1  = new RouteTimeRangeRequest(
                    LocalTime.of(6, 0), LocalTime.of(7, 30), 30, 8, false);
            RouteTimeRangeRequest r2  = new RouteTimeRangeRequest(
                    LocalTime.of(7, 0), LocalTime.of(8, 0), 30, 8, false); // overlaps r1
            RouteTimeRangeRequest ov  = overnight(22, 2);
            List<RouteTimeRangeRequest> mixed = List.of(r1, r2, ov);
            assertThatThrownBy(() -> TimeRangeValidator.validate(mixed, CTX))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("timeRanges.overlap");
        }
    }

    // ── Context key in messages ───────────────────────────────────────────────

    @Test @DisplayName("context key appears in every error message")
    void contextKey_inMessage() {
        assertThatThrownBy(() -> TimeRangeValidator.validate(null, "calendarOverride"))
                .hasMessageContaining("calendarOverride");
    }
}
