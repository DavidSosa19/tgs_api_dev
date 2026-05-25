package com.example.tgs_dev.service;

import com.example.tgs_dev.controller.request.RemoveVehicleRequest;
import com.example.tgs_dev.entity.*;
import com.example.tgs_dev.entity.enums.RemovalType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static com.example.tgs_dev.TestFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("VehicleRemovalService")
class VehicleRemovalServiceTest {

    @Mock VehicleAssignmentService vehicleAssignmentService;
    @Mock ScheduleService          scheduleService;
    @Mock RouteService             routeService;
    @Mock RouteOperationService    routeOperationService;

    @InjectMocks VehicleRemovalService sut;

    private Route            route;
    private RouteOperation   op;
    private Vehicle          vehicleA;
    private ScheduleTemplate template;

    @BeforeEach
    void setUp() {
        route    = route(1, "1");
        op       = operation(1, route, OP_DATE);
        vehicleA = vehicle(10, "V-001");
        template = template(100, route, LocalTime.of(6, 0));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Dispatch / guard clauses
    // ═══════════════════════════════════════════════════════════════════════════
    @Nested @DisplayName("Guard clauses")
    class Guards {

        @Test @DisplayName("unknown assignment id → NoSuchElementException with id in message")
        void unknownId_throws() {
            when(vehicleAssignmentService.findById(99)).thenReturn(Optional.empty());
            var req = request(99, RemovalType.REMOVE_ONLY, null);

            assertThatThrownBy(() -> sut.handleRemoval(req))
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessageContaining("99");
        }

        @Test @DisplayName("REMOVE_RECALCULATE without effectiveFrom → IllegalArgumentException")
        void recalculate_requiresEffectiveFrom() {
            stubFindById(1, assignment(1, op, vehicleA, template, 1));
            var req = request(1, RemovalType.REMOVE_RECALCULATE, null);

            assertThatThrownBy(() -> sut.handleRemoval(req))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // REMOVE_ONLY
    // ═══════════════════════════════════════════════════════════════════════════
    @Nested @DisplayName("REMOVE_ONLY")
    class RemoveOnly {

        @Test @DisplayName("soft-deletes the assignment and touches nothing else")
        void softDeletesAndStops() {
            VehicleAssignment va = assignment(1, op, vehicleA, template, 1);
            stubFindById(1, va);

            sut.handleRemoval(request(1, RemovalType.REMOVE_ONLY, null));

            verify(vehicleAssignmentService).softDelete(va);
            verifyNoInteractions(scheduleService, routeService, routeOperationService);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // REMOVE_RECALCULATE
    // ═══════════════════════════════════════════════════════════════════════════
    @Nested @DisplayName("REMOVE_RECALCULATE")
    class RemoveAndRecalculate {

        @Test @DisplayName("no vehicles after removed one → only soft-deletes, no schedule update")
        void noRemaining_noScheduleUpdate() {
            VehicleAssignment va = assignment(1, op, vehicleA, template, 3);
            stubFindById(1, va);
            when(vehicleAssignmentService.findByRouteOperationAndRowOrderGreaterThan(op, 3))
                    .thenReturn(Collections.emptyList());

            sut.handleRemoval(request(1, RemovalType.REMOVE_RECALCULATE, LocalTime.of(5, 0)));

            verify(vehicleAssignmentService).softDelete(va);
            verify(scheduleService, never()).saveAll(any());
        }

        @Test @DisplayName("no schedules on removed vehicle after effectiveFrom → only soft-deletes")
        void noSchedulesAfterEffective_noScheduleUpdate() {
            VehicleAssignment va  = assignment(1, op, vehicleA, template, 2);
            VehicleAssignment rem = assignment(2, op, vehicle(20, "V-002"), template, 3);
            stubFindById(1, va);
            when(vehicleAssignmentService.findByRouteOperationAndRowOrderGreaterThan(op, 2))
                    .thenReturn(List.of(rem));
            // all removed schedules are before effectiveFrom (08:00)
            when(scheduleService.findAllByAssignment(List.of(1)))
                    .thenReturn(List.of(
                            schedule(1, va, 1, LocalTime.of(6, 0)),
                            schedule(2, va, 2, LocalTime.of(7, 0))
                    ));
            when(scheduleService.findAllByAssignment(List.of(2)))
                    .thenReturn(List.of(schedule(3, rem, 1, LocalTime.of(9, 0))));

            sut.handleRemoval(request(1, RemovalType.REMOVE_RECALCULATE, LocalTime.of(8, 0)));

            verify(vehicleAssignmentService).softDelete(va);
            verify(scheduleService, never()).saveAll(any());
        }

        @Test @DisplayName("redistributes departure times across remaining vehicles (core algorithm)")
        void redistributesDepartureTimes() {
            // T_removed=06:00, T_last(rem2)=09:00, N=2
            // interval = (9h-6h)/2 = 90 min
            //   rem1: newBase=07:30, oldBase=07:00 → shift +30 min
            //   rem2: newBase=09:00, oldBase=09:00 → no shift
            VehicleAssignment va   = assignment(1, op, vehicleA,                  template, 2);
            VehicleAssignment rem1 = assignment(2, op, vehicle(20, "V-002"), template, 3);
            VehicleAssignment rem2 = assignment(3, op, vehicle(30, "V-003"), template, 4);

            Schedule sRem1 = schedule(20, rem1, 1, LocalTime.of(7, 0));
            Schedule sRem2 = schedule(30, rem2, 1, LocalTime.of(9, 0));

            stubFindById(1, va);
            when(vehicleAssignmentService.findByRouteOperationAndRowOrderGreaterThan(op, 2))
                    .thenReturn(List.of(rem1, rem2));
            when(scheduleService.findAllByAssignment(List.of(1)))
                    .thenReturn(List.of(schedule(10, va, 1, LocalTime.of(6, 0))));
            when(scheduleService.findAllByAssignment(List.of(2, 3)))
                    .thenReturn(List.of(sRem1, sRem2));

            sut.handleRemoval(request(1, RemovalType.REMOVE_RECALCULATE, LocalTime.of(5, 0)));

            assertThat(sRem1.getDepartureTime()).isEqualTo(LocalTime.of(7, 30));
            assertThat(sRem2.getDepartureTime()).isEqualTo(LocalTime.of(9, 0));
        }

        @Test @DisplayName("schedules before effectiveFrom are untouched")
        void schedulesBeforeEffectiveFrom_untouched() {
            // effectiveFrom = 06:00
            // rem1 has schedule at 05:30 (before) and 06:30 (after)
            VehicleAssignment va   = assignment(1, op, vehicleA, template, 2);
            VehicleAssignment rem1 = assignment(2, op, vehicle(20, "V-002"), template, 3);

            Schedule before  = schedule(4, rem1, 1, LocalTime.of(5, 30));
            Schedule after   = schedule(5, rem1, 2, LocalTime.of(6, 30));

            stubFindById(1, va);
            when(vehicleAssignmentService.findByRouteOperationAndRowOrderGreaterThan(op, 2))
                    .thenReturn(List.of(rem1));
            when(scheduleService.findAllByAssignment(List.of(1)))
                    .thenReturn(List.of(schedule(1, va, 1, LocalTime.of(6, 0))));
            when(scheduleService.findAllByAssignment(List.of(2)))
                    .thenReturn(List.of(before, after));

            sut.handleRemoval(request(1, RemovalType.REMOVE_RECALCULATE, LocalTime.of(6, 0)));

            // The schedule before effectiveFrom must not appear in the saveAll batch
            ArgumentCaptor<List<Schedule>> captor = ArgumentCaptor.forClass(List.class);
            verify(scheduleService).saveAll(captor.capture());
            assertThat(captor.getValue()).doesNotContain(before);
        }

        @Test @DisplayName("soft-deletes the removed assignment AFTER loading all schedule data")
        void softDeleteHappensAfterDataLoad() {
            // This ordering is critical: @SQLRestriction filters out inactive assignments
            VehicleAssignment va  = assignment(1, op, vehicleA, template, 2);
            VehicleAssignment rem = assignment(2, op, vehicle(20, "V-002"), template, 3);

            stubFindById(1, va);
            when(vehicleAssignmentService.findByRouteOperationAndRowOrderGreaterThan(op, 2))
                    .thenReturn(List.of(rem));
            when(scheduleService.findAllByAssignment(List.of(1)))
                    .thenReturn(List.of(schedule(1, va, 1, LocalTime.of(6, 0))));
            when(scheduleService.findAllByAssignment(List.of(2)))
                    .thenReturn(List.of(schedule(2, rem, 1, LocalTime.of(9, 0))));

            sut.handleRemoval(request(1, RemovalType.REMOVE_RECALCULATE, LocalTime.of(5, 0)));

            var ordered = inOrder(scheduleService, vehicleAssignmentService);
            ordered.verify(scheduleService, atLeastOnce()).findAllByAssignment(any());
            ordered.verify(vehicleAssignmentService).softDelete(va);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // REMOVE_REPLACE
    // ═══════════════════════════════════════════════════════════════════════════
    @Nested @DisplayName("REMOVE_REPLACE")
    class RemoveAndReplace {

        @Test @DisplayName("route '3' not found → NoSuchElementException")
        void route3NotFound() {
            stubFindById(1, assignment(1, op, vehicleA, template, 2));
            when(routeService.findByNumber("3")).thenReturn(Optional.empty());
            var req = request(1, RemovalType.REMOVE_REPLACE, null);

            assertThatThrownBy(() -> sut.handleRemoval(req))
                    .isInstanceOf(NoSuchElementException.class);
        }

        @Test @DisplayName("route 3 has no operation for the date → NoSuchElementException")
        void route3OperationNotFound() {
            Route route3 = route(3, "3");
            stubFindById(1, assignment(1, op, vehicleA, template, 2));
            when(routeService.findByNumber("3")).thenReturn(Optional.of(route3));
            when(routeOperationService.findByRouteAndDate(eq(route3), any())).thenReturn(Optional.empty());
            var req = request(1, RemovalType.REMOVE_REPLACE, null);

            assertThatThrownBy(() -> sut.handleRemoval(req))
                    .isInstanceOf(NoSuchElementException.class);
        }

        @Test @DisplayName("route 3 operation has no assignments → NoSuchElementException")
        void route3NoAssignments() {
            Route          route3 = route(3, "3");
            RouteOperation op3    = operation(3, route3, OP_DATE);
            stubFindById(1, assignment(1, op, vehicleA, template, 2));
            when(routeService.findByNumber("3")).thenReturn(Optional.of(route3));
            when(routeOperationService.findByRouteAndDate(route3, OP_DATE)).thenReturn(Optional.of(op3));
            when(vehicleAssignmentService.findLastByRouteOperation(op3)).thenReturn(Optional.empty());
            var req = request(1, RemovalType.REMOVE_REPLACE, null);

            assertThatThrownBy(() -> sut.handleRemoval(req))
                    .isInstanceOf(NoSuchElementException.class);
        }

        @Test @DisplayName("replacement has same template and rowOrder as the removed assignment")
        void replacement_preservesTemplateAndRowOrder() {
            VehicleAssignment toRemove = assignment(1, op, vehicleA, template, 2);
            stubReplaceScenario(toRemove);

            sut.handleRemoval(request(1, RemovalType.REMOVE_REPLACE, null));

            VehicleAssignment created = captureCreatedAssignment();
            assertThat(created.getScheduleTemplate()).isEqualTo(template);
            assertThat(created.getRowOrder()).isEqualTo(2);
            assertThat(created.getRouteOperation()).isEqualTo(op);
        }

        @Test @DisplayName("replacement carries REPLACEMENT origin and tracks the replaced id")
        void replacement_hasCorrectAuditFields() {
            VehicleAssignment toRemove = assignment(1, op, vehicleA, template, 2);
            stubReplaceScenario(toRemove);

            sut.handleRemoval(request(1, RemovalType.REMOVE_REPLACE, null));

            VehicleAssignment created = captureCreatedAssignment();
            assertThat(created.getOrigin()).isEqualTo("REPLACEMENT");
            assertThat(created.getReplacesId()).isEqualTo(1L);
        }

        @Test @DisplayName("original is soft-deleted as REPLACED; route-3 assignment as LOANED")
        void softDeleteReasons_areCorrect() {
            VehicleAssignment toRemove = assignment(1, op, vehicleA, template, 2);
            VehicleAssignment last3    = stubReplaceScenario(toRemove);

            sut.handleRemoval(request(1, RemovalType.REMOVE_REPLACE, null));

            verify(vehicleAssignmentService).softDeleteWithReason(toRemove, "REPLACED");
            verify(vehicleAssignmentService).softDeleteWithReason(last3, "LOANED");
        }

        @Test @DisplayName("schedules are calculated for the replacement using the original template")
        void replacement_schedulesAreGenerated() {
            VehicleAssignment toRemove = assignment(1, op, vehicleA, template, 2);
            stubReplaceScenario(toRemove);

            sut.handleRemoval(request(1, RemovalType.REMOVE_REPLACE, null));

            verify(scheduleService).calculateVehicleSchedules(argThat(list ->
                    list.size() == 1 && list.get(0).getScheduleTemplate().equals(template)));
        }

        // ── helpers ───────────────────────────────────────────────────────────

        /** Stubs a full happy-path replace scenario; returns the route-3 assignment. */
        private VehicleAssignment stubReplaceScenario(VehicleAssignment toRemove) {
            Route          route3  = route(3, "3");
            RouteOperation op3     = operation(3, route3, OP_DATE);
            Vehicle        veh3    = vehicle(30, "V-003");
            VehicleAssignment last3 = assignment(5, op3, veh3, template, 3);

            stubFindById(toRemove.getId(), toRemove);
            when(routeService.findByNumber("3")).thenReturn(Optional.of(route3));
            when(routeOperationService.findByRouteAndDate(route3, OP_DATE)).thenReturn(Optional.of(op3));
            when(vehicleAssignmentService.findLastByRouteOperation(op3)).thenReturn(Optional.of(last3));
            when(vehicleAssignmentService.save(any())).thenAnswer(inv -> {
                VehicleAssignment saved = inv.getArgument(0);
                saved.setId(99);
                return saved;
            });
            return last3;
        }

        private VehicleAssignment captureCreatedAssignment() {
            ArgumentCaptor<VehicleAssignment> captor = ArgumentCaptor.forClass(VehicleAssignment.class);
            verify(vehicleAssignmentService).save(captor.capture());
            return captor.getValue();
        }
    }

    // ── Shared helpers ─────────────────────────────────────────────────────────
    private void stubFindById(int id, VehicleAssignment va) {
        when(vehicleAssignmentService.findById(id)).thenReturn(Optional.of(va));
    }

    private static RemoveVehicleRequest request(int id, RemovalType type, LocalTime effective) {
        return new RemoveVehicleRequest(id, type, effective);
    }
}
