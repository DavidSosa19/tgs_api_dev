package com.example.tgs_dev.service.removal;

import com.example.tgs_dev.controller.exception.BusinessException;
import com.example.tgs_dev.entity.Route;
import com.example.tgs_dev.entity.RouteOperation;
import com.example.tgs_dev.entity.Schedule;
import com.example.tgs_dev.entity.VehicleAssignment;
import com.example.tgs_dev.entity.enums.RecalculationScope;
import com.example.tgs_dev.entity.enums.RemovalType;
import com.example.tgs_dev.entity.enums.ScheduleOrigin;
import com.example.tgs_dev.service.ScheduleService;
import com.example.tgs_dev.service.VehicleAssignmentService;
import com.example.tgs_dev.service.removal.recalculation.RecalculationAlgorithm;
import com.example.tgs_dev.service.removal.recalculation.RecalculationContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.example.tgs_dev.TestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RecalculateHeadwayStrategy}.
 *
 * <p>Algorithms ({@link RecalculationAlgorithm}) are mocked — the shift math
 * is tested separately in the algorithm test classes.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RecalculateHeadwayStrategy")
class RecalculateHeadwayStrategyTest {

    @Mock VehicleAssignmentService vehicleAssignmentService;
    @Mock ScheduleService          scheduleService;
    @Mock RecalculationAlgorithm   allVehiclesAlgorithm;
    @Mock RecalculationAlgorithm   subsequentAlgorithm;

    private RecalculateHeadwayStrategy sut;

    private final LocalDateTime now = LocalDateTime.now();

    private Route             route;
    private RouteOperation    op;
    private VehicleAssignment toRemove;

    @BeforeEach
    void setUp() {
        when(allVehiclesAlgorithm.scope()).thenReturn(RecalculationScope.ALL_VEHICLES);
        when(subsequentAlgorithm.scope()).thenReturn(RecalculationScope.SUBSEQUENT_X);
        sut = new RecalculateHeadwayStrategy(
                vehicleAssignmentService, scheduleService,
                List.of(allVehiclesAlgorithm, subsequentAlgorithm)
        );

        route    = route(1, "1");
        op       = operation(1, route, OP_DATE);
        toRemove = assignment(1, op, vehicle(10, "V-001"), template(100, route, 1), 2);
    }

    @Test @DisplayName("supports REMOVE_RECALCULATE")
    void supports_removeRecalculate() {
        assertThat(sut.supports()).isEqualTo(RemovalType.REMOVE_RECALCULATE);
    }

    // ── Validation ────────────────────────────────────────────────────────────

    @Nested @DisplayName("input validation")
    class Validation {

        @Test @DisplayName("missing fromTime → BusinessException")
        void missingFromTime() {
            RemovalContext ctx = ctx(null, RecalculationScope.ALL_VEHICLES, null);
            assertThatThrownBy(() -> sut.execute(ctx))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("fromTime.required");
        }

        @Test @DisplayName("missing recalculationScope → BusinessException")
        void missingScope() {
            RemovalContext ctx = ctx(LocalTime.of(6, 0), null, null);
            assertThatThrownBy(() -> sut.execute(ctx))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("recalculationScope.required");
        }

        @Test @DisplayName("SUBSEQUENT_X without windowSize → BusinessException")
        void subsequentX_missingWindowSize() {
            RemovalContext ctx = ctx(LocalTime.of(6, 0), RecalculationScope.SUBSEQUENT_X, null);
            assertThatThrownBy(() -> sut.execute(ctx))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("windowSize.required");
        }

        @Test @DisplayName("SUBSEQUENT_X with windowSize <= 0 → BusinessException")
        void subsequentX_nonPositiveWindowSize() {
            RemovalContext ctx = ctx(LocalTime.of(6, 0), RecalculationScope.SUBSEQUENT_X, 0);
            assertThatThrownBy(() -> sut.execute(ctx))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("windowSize.mustBePositive");
        }
    }

    // ── Short-circuit paths ───────────────────────────────────────────────────

    @Nested @DisplayName("short-circuit: soft-delete only")
    class ShortCircuit {

        @Test @DisplayName("no candidates after removed → soft-delete only, algorithm not invoked")
        void noCandidates_noAlgorithm() {
            when(vehicleAssignmentService.findByRouteOperationAndRowOrderGreaterThan(op, 2))
                    .thenReturn(Collections.emptyList());
            when(scheduleService.findAllByAssignment(anyList())).thenReturn(List.of());

            sut.execute(ctx(LocalTime.of(5, 0), RecalculationScope.ALL_VEHICLES, null));

            verify(vehicleAssignmentService).softDelete(toRemove);
            verify(allVehiclesAlgorithm, never()).computeShifts(any());
            verify(subsequentAlgorithm,  never()).computeShifts(any());
        }

        @Test @DisplayName("no qualifying schedules on removed vehicle → soft-delete only")
        void noQualifyingSchedules_noAlgorithm() {
            VehicleAssignment rem = assignment(2, op, vehicle(20, "V-002"), template(100, route, 1), 3);
            when(vehicleAssignmentService.findByRouteOperationAndRowOrderGreaterThan(op, 2))
                    .thenReturn(List.of(rem));
            when(scheduleService.findAllByAssignment(anyList()))
                    .thenReturn(List.of(
                            schedule(1, toRemove, 1, LocalTime.of(6, 0)),
                            schedule(2, toRemove, 2, LocalTime.of(7, 0))
                    ));

            sut.execute(ctx(LocalTime.of(8, 0), RecalculationScope.ALL_VEHICLES, null));

            verify(vehicleAssignmentService).softDelete(toRemove);
            verify(allVehiclesAlgorithm, never()).computeShifts(any());
        }
    }

    // ── Algorithm dispatch & lifecycle ───────────────────────────────────────

    @Nested @DisplayName("algorithm dispatch and lifecycle")
    class Dispatch {

        @Test @DisplayName("dispatches to ALL_VEHICLES with full candidates window")
        void dispatches_allVehicles() {
            VehicleAssignment rem1 = assignment(2, op, vehicle(20, "V-002"), template(100, route, 1), 3);
            VehicleAssignment rem2 = assignment(3, op, vehicle(30, "V-003"), template(100, route, 1), 4);
            stubBatch(List.of(rem1, rem2));
            when(allVehiclesAlgorithm.computeShifts(any())).thenReturn(Map.of());

            sut.execute(ctx(LocalTime.of(5, 0), RecalculationScope.ALL_VEHICLES, null));

            ArgumentCaptor<RecalculationContext> captor = ArgumentCaptor.forClass(RecalculationContext.class);
            verify(allVehiclesAlgorithm).computeShifts(captor.capture());
            assertThat(captor.getValue().windowSize()).isEqualTo(2);
            verify(subsequentAlgorithm, never()).computeShifts(any());
        }

        @Test @DisplayName("dispatches to SUBSEQUENT_X with user-supplied window size")
        void dispatches_subsequentX() {
            VehicleAssignment rem1 = assignment(2, op, vehicle(20, "V-002"), template(100, route, 1), 3);
            VehicleAssignment rem2 = assignment(3, op, vehicle(30, "V-003"), template(100, route, 1), 4);
            stubBatch(List.of(rem1, rem2));
            when(subsequentAlgorithm.computeShifts(any())).thenReturn(Map.of());

            sut.execute(ctx(LocalTime.of(5, 0), RecalculationScope.SUBSEQUENT_X, 1));

            ArgumentCaptor<RecalculationContext> captor = ArgumentCaptor.forClass(RecalculationContext.class);
            verify(subsequentAlgorithm).computeShifts(captor.capture());
            assertThat(captor.getValue().windowSize()).isEqualTo(1);
        }

        @Test @DisplayName("context carries only qualifying schedules (>= fromTime)")
        void contextContainsOnlyQualifyingSchedules() {
            VehicleAssignment rem = assignment(2, op, vehicle(20, "V-002"), template(100, route, 1), 3);
            when(vehicleAssignmentService.findByRouteOperationAndRowOrderGreaterThan(op, 2))
                    .thenReturn(List.of(rem));
            Schedule removed = schedule(1, toRemove, 1, LocalTime.of(6, 0));
            Schedule before  = schedule(10, rem, 1, LocalTime.of(5, 30));
            Schedule after   = schedule(11, rem, 2, LocalTime.of(6, 30));
            when(scheduleService.findAllByAssignment(anyList()))
                    .thenReturn(List.of(removed, before, after));
            when(allVehiclesAlgorithm.computeShifts(any())).thenReturn(Map.of());

            sut.execute(ctx(LocalTime.of(6, 0), RecalculationScope.ALL_VEHICLES, null));

            ArgumentCaptor<RecalculationContext> captor = ArgumentCaptor.forClass(RecalculationContext.class);
            verify(allVehiclesAlgorithm).computeShifts(captor.capture());
            List<Schedule> candidateSchedules = captor.getValue().qualifyingSchedules()
                    .getOrDefault(rem.getId(), List.of());
            assertThat(candidateSchedules).containsOnly(after).doesNotContain(before);
        }

        @Test @DisplayName("batches removed + candidate schedules in a single query")
        void singleBatchQuery() {
            VehicleAssignment rem1 = assignment(2, op, vehicle(20, "V-002"), template(100, route, 1), 3);
            VehicleAssignment rem2 = assignment(3, op, vehicle(30, "V-003"), template(100, route, 1), 4);
            stubBatch(List.of(rem1, rem2));
            when(allVehiclesAlgorithm.computeShifts(any())).thenReturn(Map.of());

            sut.execute(ctx(LocalTime.of(5, 0), RecalculationScope.ALL_VEHICLES, null));

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<Integer>> idsCaptor = ArgumentCaptor.forClass(List.class);
            verify(scheduleService, times(1)).findAllByAssignment(idsCaptor.capture());
            assertThat(idsCaptor.getValue()).containsExactlyInAnyOrder(1, 2, 3);
        }

        @Test @DisplayName("removed vehicle's qualifying schedules → superseded VEHICLE_REMOVED")
        void removedSchedules_superseded() {
            VehicleAssignment rem = assignment(2, op, vehicle(20, "V-002"), template(100, route, 1), 3);
            Schedule removedSched = schedule(1, toRemove, 1, LocalTime.of(10, 0));
            Schedule candSched    = schedule(2, rem,      1, LocalTime.of(10, 5));

            when(vehicleAssignmentService.findByRouteOperationAndRowOrderGreaterThan(op, 2))
                    .thenReturn(List.of(rem));
            when(scheduleService.findAllByAssignment(anyList()))
                    .thenReturn(List.of(removedSched, candSched));
            when(allVehiclesAlgorithm.computeShifts(any())).thenReturn(Map.of());

            sut.execute(ctx(LocalTime.of(9, 0), RecalculationScope.ALL_VEHICLES, null));

            assertThat(removedSched.getActive()).isFalse();
            assertThat(removedSched.getSupersededReason())
                    .isEqualTo(ScheduleSupersededReason.VEHICLE_REMOVED);
        }

        @Test @DisplayName("trips < tRemoved are not shifted even if they qualify (cycle guard)")
        void cycleGuard_skipsTripsBeforeRemovedSlot() {
            // Operator scenario: H=13 min, fromTime=15:30, eliminate v1.
            // v1's qualifying = [17:25] (its 15:02 is < fromTime, stays historical).
            // v4 has TWO qualifying trips: 15:41 (cycle s1) and 18:04 (cycle s2).
            // The cycle-s1 trip (15:41 < tRemoved=17:25) must NOT shift —
            // its cycle's slot for v1 (15:02) is preserved as history.
            VehicleAssignment v4 = assignment(4, op, vehicle(40, "V-004"),
                                              template(100, route, 1), 5);

            Schedule v1s2 = schedule(101, toRemove, 2, LocalTime.of(17, 25));
            Schedule v4s1 = schedule(401, v4,       1, LocalTime.of(15, 41)); // pre-tRemoved
            Schedule v4s2 = schedule(402, v4,       2, LocalTime.of(18,  4));

            when(vehicleAssignmentService.findByRouteOperationAndRowOrderGreaterThan(op, 2))
                    .thenReturn(List.of(v4));
            when(scheduleService.findAllByAssignment(anyList()))
                    .thenReturn(List.of(v1s2, v4s1, v4s2));
            when(allVehiclesAlgorithm.computeShifts(any()))
                    .thenReturn(Map.of(v4.getId(), -6L));

            sut.execute(ctx(LocalTime.of(15, 30), RecalculationScope.ALL_VEHICLES, null));

            // v4.s1 untouched (pre-tRemoved)
            assertThat(v4s1.getActive()).isTrue();
            assertThat(v4s1.getDepartureTime()).isEqualTo(LocalTime.of(15, 41));

            // v4.s2 superseded and replaced with shifted RECALCULATED row
            assertThat(v4s2.getActive()).isFalse();

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<Schedule>> insertCaptor = ArgumentCaptor.forClass(List.class);
            verify(scheduleService).saveAll(insertCaptor.capture());
            List<Schedule> inserts = insertCaptor.getValue();
            assertThat(inserts).hasSize(1);   // only the s2 replacement, not s1
            assertThat(inserts.getFirst().getDepartureTime()).isEqualTo(LocalTime.of(17, 58));
            assertThat(inserts.getFirst().getOriginalDepartureTime()).isEqualTo(LocalTime.of(18, 4));
        }

        @Test @DisplayName("for shifted candidate: old superseded RECALCULATED, new row created")
        void shiftCreatesNewRowAndSupersedesOld() {
            VehicleAssignment rem = assignment(2, op, vehicle(20, "V-002"), template(100, route, 1), 3);
            Schedule removedSched = schedule(1, toRemove, 1, LocalTime.of(10, 0));
            Schedule candSched    = schedule(2, rem,      1, LocalTime.of(10, 5));

            when(vehicleAssignmentService.findByRouteOperationAndRowOrderGreaterThan(op, 2))
                    .thenReturn(List.of(rem));
            when(scheduleService.findAllByAssignment(anyList()))
                    .thenReturn(List.of(removedSched, candSched));
            when(allVehiclesAlgorithm.computeShifts(any()))
                    .thenReturn(Map.of(rem.getId(), -5L));

            sut.execute(ctx(LocalTime.of(9, 0), RecalculationScope.ALL_VEHICLES, null));

            // candidate's old row superseded
            assertThat(candSched.getActive()).isFalse();
            assertThat(candSched.getSupersededReason())
                    .isEqualTo(ScheduleSupersededReason.RECALCULATED);

            // saveAll receives both supersededs + the new RECALCULATED row
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<Schedule>> captor = ArgumentCaptor.forClass(List.class);
            verify(scheduleService).saveAll(captor.capture());
            List<Schedule> saved = captor.getValue();

            Schedule created = saved.stream()
                    .filter(s -> s.getOrigin() == ScheduleOrigin.RECALCULATED)
                    .findFirst().orElseThrow();
            assertThat(created.getDepartureTime()).isEqualTo(LocalTime.of(10, 0));   // 10:05 − 5
            assertThat(created.getTripNumber()).isEqualTo(1);
            assertThat(created.getOriginalDepartureTime()).isEqualTo(LocalTime.of(10, 5));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private RemovalContext ctx(LocalTime fromTime, RecalculationScope scope, Integer windowSize) {
        return new RemovalContext(toRemove, now, fromTime, scope, windowSize, null);
    }

    private void stubBatch(List<VehicleAssignment> candidates) {
        when(vehicleAssignmentService.findByRouteOperationAndRowOrderGreaterThan(op, 2))
                .thenReturn(candidates);

        Schedule removed = schedule(1, toRemove, 1, LocalTime.of(6, 0));
        List<Schedule> all = new ArrayList<>();
        all.add(removed);
        for (VehicleAssignment c : candidates) {
            all.add(schedule(c.getId() * 10, c, 1, LocalTime.of(9, 0)));
        }
        when(scheduleService.findAllByAssignment(anyList())).thenReturn(all);
    }
}
