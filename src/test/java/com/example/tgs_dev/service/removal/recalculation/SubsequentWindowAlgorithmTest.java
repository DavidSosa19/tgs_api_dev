package com.example.tgs_dev.service.removal.recalculation;

import com.example.tgs_dev.entity.Route;
import com.example.tgs_dev.entity.RouteOperation;
import com.example.tgs_dev.entity.Schedule;
import com.example.tgs_dev.entity.VehicleAssignment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static com.example.tgs_dev.TestFixtures.*;
import static com.example.tgs_dev.entity.enums.RecalculationScope.SUBSEQUENT_X;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SubsequentWindowAlgorithm}.
 *
 * <p>Verifies the <em>stretched-headway</em> shift formula returned by
 * {@code computeShifts}: the first candidate shifts by {@code -gap}, each
 * subsequent within the window shifts by progressively less.  Persistence is
 * the strategy's concern — tests here verify only the math.
 */
@DisplayName("SubsequentWindowAlgorithm")
class SubsequentWindowAlgorithmTest {

    private final SubsequentWindowAlgorithm sut = new SubsequentWindowAlgorithm();

    private final Route          route = route(1, "1");
    private final RouteOperation op    = operation(1, route, OP_DATE);

    @Test @DisplayName("supports SUBSEQUENT_X scope")
    void supports_subsequentX() {
        assertThat(sut.scope()).isEqualTo(SUBSEQUENT_X);
    }

    // ── No-op paths ───────────────────────────────────────────────────────────

    @Nested @DisplayName("no qualifying schedules in window")
    class NoSchedules {

        @Test @DisplayName("no candidates have qualifying schedules → empty map")
        void noQualifyingSchedules_emptyMap() {
            VehicleAssignment va  = assignment(1, op, vehicle(10, "V-001"), template(100, route, 1), 2);
            VehicleAssignment rem = assignment(2, op, vehicle(20, "V-002"), template(100, route, 1), 3);
            Schedule removed = schedule(1, va, 1, LocalTime.of(6, 0));

            RecalculationContext ctx = new RecalculationContext(
                    List.of(removed), List.of(rem), Map.of(), 1);

            assertThat(sut.computeShifts(ctx)).isEmpty();
        }

        @Test @DisplayName("window=0 → empty map")
        void windowZero_emptyMap() {
            VehicleAssignment va  = assignment(1, op, vehicle(10, "V-001"), template(100, route, 1), 2);
            VehicleAssignment rem = assignment(2, op, vehicle(20, "V-002"), template(100, route, 1), 3);
            Schedule removed = schedule(1, va, 1, LocalTime.of(6, 0));
            Schedule sRem    = schedule(20, rem, 1, LocalTime.of(6, 10));

            RecalculationContext ctx = new RecalculationContext(
                    List.of(removed), List.of(rem), Map.of(rem.getId(), List.of(sRem)), 0);

            assertThat(sut.computeShifts(ctx)).isEmpty();
        }
    }

    // ── Stretched-headway math ───────────────────────────────────────────────

    @Nested @DisplayName("stretched headway shifts")
    class StretchedHeadway {

        /** Operator canonical example: H=5, gap=5, window=5 → shifts -5,-4,-3,-2,-1. */
        @Test @DisplayName("5 vehicles each 5 min apart, X=5 → shifts -5,-4,-3,-2,-1")
        void operatorExample_fiveByFive() {
            VehicleAssignment v1 = assignment(1, op, vehicle(10, "V-001"), template(100, route, 1), 1);
            VehicleAssignment v2 = assignment(2, op, vehicle(20, "V-002"), template(100, route, 1), 2);
            VehicleAssignment v3 = assignment(3, op, vehicle(30, "V-003"), template(100, route, 1), 3);
            VehicleAssignment v4 = assignment(4, op, vehicle(40, "V-004"), template(100, route, 1), 4);
            VehicleAssignment v5 = assignment(5, op, vehicle(50, "V-005"), template(100, route, 1), 5);
            VehicleAssignment v6 = assignment(6, op, vehicle(60, "V-006"), template(100, route, 1), 6);

            Schedule sRemoved = schedule(10, v1, 1, LocalTime.of(6,  0));
            Schedule s2 = schedule(20, v2, 1, LocalTime.of(6,  5));
            Schedule s3 = schedule(30, v3, 1, LocalTime.of(6, 10));
            Schedule s4 = schedule(40, v4, 1, LocalTime.of(6, 15));
            Schedule s5 = schedule(50, v5, 1, LocalTime.of(6, 20));
            Schedule s6 = schedule(60, v6, 1, LocalTime.of(6, 25));

            RecalculationContext ctx = new RecalculationContext(
                    List.of(sRemoved),
                    List.of(v2, v3, v4, v5, v6),
                    Map.of(v2.getId(), List.of(s2),
                           v3.getId(), List.of(s3),
                           v4.getId(), List.of(s4),
                           v5.getId(), List.of(s5),
                           v6.getId(), List.of(s6)),
                    5
            );

            Map<Integer, Long> shifts = sut.computeShifts(ctx);

            assertThat(shifts).containsEntry(v2.getId(), -5L);
            assertThat(shifts).containsEntry(v3.getId(), -4L);
            assertThat(shifts).containsEntry(v4.getId(), -3L);
            assertThat(shifts).containsEntry(v5.getId(), -2L);
            assertThat(shifts).containsEntry(v6.getId(), -1L);
        }

        @Test @DisplayName("window=1 → first candidate gets -gap, no other entries")
        void window1_singleEntry() {
            VehicleAssignment va   = assignment(1, op, vehicle(10, "V-001"), template(100, route, 1), 2);
            VehicleAssignment rem1 = assignment(2, op, vehicle(20, "V-002"), template(100, route, 1), 3);
            VehicleAssignment rem2 = assignment(3, op, vehicle(30, "V-003"), template(100, route, 1), 4);

            Schedule sRemoved = schedule(10, va,   1, LocalTime.of(6,  0));
            Schedule sRem1    = schedule(20, rem1, 1, LocalTime.of(6, 10));
            Schedule sRem2    = schedule(30, rem2, 1, LocalTime.of(6, 20));

            RecalculationContext ctx = new RecalculationContext(
                    List.of(sRemoved),
                    List.of(rem1, rem2),
                    Map.of(rem1.getId(), List.of(sRem1), rem2.getId(), List.of(sRem2)),
                    1
            );

            Map<Integer, Long> shifts = sut.computeShifts(ctx);

            assertThat(shifts).containsExactlyEntriesOf(Map.of(rem1.getId(), -10L));
        }

        @Test @DisplayName("window=2 → shifts -gap and -gap/2")
        void window2_progressiveShifts() {
            VehicleAssignment va   = assignment(1, op, vehicle(10, "V-001"), template(100, route, 1), 2);
            VehicleAssignment rem1 = assignment(2, op, vehicle(20, "V-002"), template(100, route, 1), 3);
            VehicleAssignment rem2 = assignment(3, op, vehicle(30, "V-003"), template(100, route, 1), 4);

            Schedule sRemoved = schedule(10, va,   1, LocalTime.of(6,  0));
            Schedule sRem1    = schedule(20, rem1, 1, LocalTime.of(6, 10));
            Schedule sRem2    = schedule(30, rem2, 1, LocalTime.of(6, 20));

            RecalculationContext ctx = new RecalculationContext(
                    List.of(sRemoved),
                    List.of(rem1, rem2),
                    Map.of(rem1.getId(), List.of(sRem1), rem2.getId(), List.of(sRem2)),
                    2
            );

            Map<Integer, Long> shifts = sut.computeShifts(ctx);
            assertThat(shifts).containsEntry(rem1.getId(), -10L);
            assertThat(shifts).containsEntry(rem2.getId(),  -5L);
        }

        @Test @DisplayName("window > candidates.size → caps at candidates.size")
        void windowExceedsCandidates_capped() {
            VehicleAssignment va   = assignment(1, op, vehicle(10, "V-001"), template(100, route, 1), 2);
            VehicleAssignment rem1 = assignment(2, op, vehicle(20, "V-002"), template(100, route, 1), 3);

            Schedule sRemoved = schedule(10, va,   1, LocalTime.of(6,  0));
            Schedule sRem1    = schedule(20, rem1, 1, LocalTime.of(6, 15));

            RecalculationContext ctx = new RecalculationContext(
                    List.of(sRemoved),
                    List.of(rem1),
                    Map.of(rem1.getId(), List.of(sRem1)),
                    99
            );

            Map<Integer, Long> shifts = sut.computeShifts(ctx);
            assertThat(shifts).containsExactlyEntriesOf(Map.of(rem1.getId(), -15L));
        }

        @Test @DisplayName("zero-shift entries are omitted from the map")
        void zeroShiftsOmitted() {
            // Last candidate in a window=2 would otherwise get -gap/2 = floor(-1/2) = 0
            // when gap=1.  This test verifies such zero entries are dropped.
            VehicleAssignment va   = assignment(1, op, vehicle(10, "V-001"), template(100, route, 1), 2);
            VehicleAssignment rem1 = assignment(2, op, vehicle(20, "V-002"), template(100, route, 1), 3);
            VehicleAssignment rem2 = assignment(3, op, vehicle(30, "V-003"), template(100, route, 1), 4);

            Schedule sRemoved = schedule(10, va,   1, LocalTime.of(6,  0));
            Schedule sRem1    = schedule(20, rem1, 1, LocalTime.of(6,  1));   // gap = 1
            Schedule sRem2    = schedule(30, rem2, 1, LocalTime.of(6,  2));

            RecalculationContext ctx = new RecalculationContext(
                    List.of(sRemoved),
                    List.of(rem1, rem2),
                    Map.of(rem1.getId(), List.of(sRem1), rem2.getId(), List.of(sRem2)),
                    2
            );

            Map<Integer, Long> shifts = sut.computeShifts(ctx);
            // gap=1, i=0: -1; i=1: 1*(1-2)/2 = -1/2 = 0 (Java truncates toward zero) → omitted
            assertThat(shifts).containsExactlyEntriesOf(Map.of(rem1.getId(), -1L));
        }
    }
}
