package com.example.tgs_dev.service.removal;

import com.example.tgs_dev.controller.exception.BusinessException;
import com.example.tgs_dev.entity.Route;
import com.example.tgs_dev.entity.RouteOperation;
import com.example.tgs_dev.entity.Schedule;
import com.example.tgs_dev.entity.Vehicle;
import com.example.tgs_dev.entity.VehicleAssignment;
import com.example.tgs_dev.entity.enums.RemovalType;
import com.example.tgs_dev.entity.enums.ScheduleOrigin;
import com.example.tgs_dev.service.RouteOperationService;
import com.example.tgs_dev.service.RouteService;
import com.example.tgs_dev.service.ScheduleService;
import com.example.tgs_dev.service.VehicleAssignmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static com.example.tgs_dev.TestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReplaceFromRouteStrategy")
class ReplaceFromRouteStrategyTest {

    @Mock VehicleAssignmentService vehicleAssignmentService;
    @Mock ScheduleService          scheduleService;
    @Mock RouteService             routeService;
    @Mock RouteOperationService    routeOperationService;
    @InjectMocks ReplaceFromRouteStrategy sut;

    private final LocalTime     fromTime = LocalTime.of(9, 0);
    private final LocalDateTime now      = LocalDateTime.now();

    private Route             mainRoute;
    private RouteOperation    mainOp;
    private VehicleAssignment toRemove;

    @BeforeEach
    void setUp() {
        mainRoute = route(1, "1");
        mainOp    = operation(1, mainRoute, OP_DATE);
        toRemove  = assignment(1, mainOp, vehicle(10, "V-001"), template(100, mainRoute, 1), 2);
    }

    @Test @DisplayName("supports REMOVE_REPLACE")
    void supports_removeReplace() {
        assertThat(sut.supports()).isEqualTo(RemovalType.REMOVE_REPLACE);
    }

    @Nested @DisplayName("guard clauses")
    class Guards {

        @Test @DisplayName("missing fromTime → BusinessException")
        void missingFromTime() {
            RemovalContext ctx = new RemovalContext(toRemove, now, null, null, null, 3L);
            assertThatThrownBy(() -> sut.execute(ctx))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("fromTime.required");
        }

        @Test @DisplayName("missing sourceRouteGroupId → BusinessException")
        void missingSourceRouteGroupId() {
            RemovalContext ctx = new RemovalContext(toRemove, now, fromTime, null, null, null);
            assertThatThrownBy(() -> sut.execute(ctx))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("sourceRouteGroupId.required");
        }

        @Test @DisplayName("donor route is the same as the operation's route → BusinessException")
        void donorRouteSameAsTarget() {
            when(routeService.findByGroupId(1L)).thenReturn(mainRoute);
            RemovalContext ctx = new RemovalContext(toRemove, now, fromTime, null, null, 1L);

            assertThatThrownBy(() -> sut.execute(ctx))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("donorRoute.sameAsTarget");
        }

        @Test @DisplayName("donor route not found → propagated")
        void donorRouteNotFound() {
            when(routeService.findByGroupId(3L))
                    .thenThrow(new NoSuchElementException("notFound.route|3"));
            RemovalContext ctx = new RemovalContext(toRemove, now, fromTime, null, null, 3L);

            assertThatThrownBy(() -> sut.execute(ctx)).isInstanceOf(NoSuchElementException.class);
        }

        @Test @DisplayName("donor route has no operation on the service date → NoSuchElementException")
        void donorOperationNotFound() {
            Route donor = route(3, "3");
            when(routeService.findByGroupId(3L)).thenReturn(donor);
            when(routeOperationService.findByRouteAndDate(donor, OP_DATE)).thenReturn(Optional.empty());

            RemovalContext ctx = new RemovalContext(toRemove, now, fromTime, null, null, 3L);
            assertThatThrownBy(() -> sut.execute(ctx)).isInstanceOf(NoSuchElementException.class);
        }

        @Test @DisplayName("donor operation has no assignments → NoSuchElementException")
        void donorNoAssignments() {
            Route          donor   = route(3, "3");
            RouteOperation donorOp = operation(3, donor, OP_DATE);
            when(routeService.findByGroupId(3L)).thenReturn(donor);
            when(routeOperationService.findByRouteAndDate(donor, OP_DATE)).thenReturn(Optional.of(donorOp));
            when(vehicleAssignmentService.findLastByRouteOperation(donorOp)).thenReturn(Optional.empty());

            RemovalContext ctx = new RemovalContext(toRemove, now, fromTime, null, null, 3L);
            assertThatThrownBy(() -> sut.execute(ctx)).isInstanceOf(NoSuchElementException.class);
        }
    }

    @Nested @DisplayName("happy path")
    class HappyPath {

        private VehicleAssignment donorAssignment;

        @BeforeEach
        void stubScenario() {
            donorAssignment = stubFullScenario();
        }

        @Test @DisplayName("replacement inherits removed vehicle's template and row order")
        void replacement_inheritsTemplateAndRowOrder() {
            stubEmptySchedules();
            sut.execute(new RemovalContext(toRemove, now, fromTime, null, null, 3L));

            VehicleAssignment created = captureCreatedAssignment();
            assertThat(created.getScheduleTemplate()).isEqualTo(toRemove.getScheduleTemplate());
            assertThat(created.getRowOrder()).isEqualTo(toRemove.getRowOrder());
            assertThat(created.getRouteOperation()).isEqualTo(mainOp);
        }

        @Test @DisplayName("replacement origin=REPLACEMENT, replacesId points to original")
        void replacement_auditFields() {
            stubEmptySchedules();
            sut.execute(new RemovalContext(toRemove, now, fromTime, null, null, 3L));

            VehicleAssignment created = captureCreatedAssignment();
            assertThat(created.getOrigin()).isEqualTo(VehicleAssignmentOrigin.REPLACEMENT);
            assertThat(created.getReplacesId()).isEqualTo(toRemove.getId().longValue());
        }

        @Test @DisplayName("eliminated's schedules < fromTime stay active; >= fromTime get REPLACED")
        void eliminatedSchedules_partitionedByFromTime() {
            Schedule before = schedule(101, toRemove, 1, LocalTime.of(6, 0));
            Schedule after1 = schedule(102, toRemove, 2, LocalTime.of(10, 0));
            Schedule after2 = schedule(103, toRemove, 3, LocalTime.of(12, 0));

            when(scheduleService.findAllByAssignment(List.of(toRemove.getId())))
                    .thenReturn(List.of(before, after1, after2));
            when(scheduleService.findAllByAssignment(List.of(donorAssignment.getId())))
                    .thenReturn(List.of());

            sut.execute(new RemovalContext(toRemove, now, fromTime, null, null, 3L));

            assertThat(before.getActive()).isTrue();
            assertThat(after1.getActive()).isFalse();
            assertThat(after1.getSupersededReason()).isEqualTo(ScheduleSupersededReason.REPLACED);
            assertThat(after2.getActive()).isFalse();
            assertThat(after2.getSupersededReason()).isEqualTo(ScheduleSupersededReason.REPLACED);
        }

        @Test @DisplayName("creates REPLACEMENT schedules with inherited times for the replacement")
        void replacement_schedulesInheritTimes() {
            Schedule after = schedule(102, toRemove, 2, LocalTime.of(10, 0));
            when(scheduleService.findAllByAssignment(List.of(toRemove.getId())))
                    .thenReturn(List.of(after));
            when(scheduleService.findAllByAssignment(List.of(donorAssignment.getId())))
                    .thenReturn(List.of());

            sut.execute(new RemovalContext(toRemove, now, fromTime, null, null, 3L));

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<Schedule>> captor = ArgumentCaptor.forClass(List.class);
            verify(scheduleService).saveAll(captor.capture());
            List<Schedule> saved = captor.getValue();

            Schedule replacement = saved.stream()
                    .filter(s -> s.getOrigin() == ScheduleOrigin.REPLACEMENT)
                    .findFirst().orElseThrow();
            assertThat(replacement.getDepartureTime()).isEqualTo(LocalTime.of(10, 0));
            assertThat(replacement.getTripNumber()).isEqualTo(2);
            // For an ORIGINAL source row, originalDepartureTime = the source's own time
            assertThat(replacement.getOriginalDepartureTime()).isEqualTo(LocalTime.of(10, 0));
        }

        @Test @DisplayName("donor schedules >= fromTime get superseded as LOANED")
        void donorSchedules_loaned() {
            Schedule donorBefore = schedule(201, donorAssignment, 1, LocalTime.of(7, 0));
            Schedule donorAfter  = schedule(202, donorAssignment, 2, LocalTime.of(11, 0));

            when(scheduleService.findAllByAssignment(List.of(toRemove.getId())))
                    .thenReturn(List.of());
            when(scheduleService.findAllByAssignment(List.of(donorAssignment.getId())))
                    .thenReturn(List.of(donorBefore, donorAfter));

            sut.execute(new RemovalContext(toRemove, now, fromTime, null, null, 3L));

            assertThat(donorBefore.getActive()).isTrue();
            assertThat(donorAfter.getActive()).isFalse();
            assertThat(donorAfter.getSupersededReason()).isEqualTo(ScheduleSupersededReason.LOANED);
        }

        @Test @DisplayName("original soft-deleted REPLACED with replacedById set")
        void original_softDeletedWithLink() {
            stubEmptySchedules();
            sut.execute(new RemovalContext(toRemove, now, fromTime, null, null, 3L));

            assertThat(toRemove.getReplacedById()).isEqualTo(99L);
            verify(vehicleAssignmentService).softDeleteWithReason(toRemove, VehicleRemovalReason.REPLACED);
        }

        @Test @DisplayName("donor assignment soft-deleted as LOANED")
        void donor_softDeletedAsLoaned() {
            stubEmptySchedules();
            sut.execute(new RemovalContext(toRemove, now, fromTime, null, null, 3L));

            verify(vehicleAssignmentService).softDeleteWithReason(donorAssignment,
                                                                  VehicleRemovalReason.LOANED);
        }

        @Test @DisplayName("returns outcome with the replacement's id")
        void returns_replacementOutcome() {
            stubEmptySchedules();
            var outcome = sut.execute(new RemovalContext(toRemove, now, fromTime, null, null, 3L));

            assertThat(outcome.replacementAssignmentId()).isEqualTo(99);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private VehicleAssignment stubFullScenario() {
        Route          donor        = route(3, "3");
        RouteOperation donorOp      = operation(3, donor, OP_DATE);
        Vehicle        donorVehicle = vehicle(30, "V-DONOR");
        VehicleAssignment donor3    = assignment(5, donorOp, donorVehicle,
                                                  template(100, mainRoute, 1), 3);
        when(routeService.findByGroupId(3L)).thenReturn(donor);
        when(routeOperationService.findByRouteAndDate(donor, OP_DATE)).thenReturn(Optional.of(donorOp));
        when(vehicleAssignmentService.findLastByRouteOperation(donorOp)).thenReturn(Optional.of(donor3));
        when(vehicleAssignmentService.save(any())).thenAnswer(inv -> {
            VehicleAssignment s = inv.getArgument(0);
            s.setId(99);
            return s;
        });
        return donor3;
    }

    private void stubEmptySchedules() {
        lenient().when(scheduleService.findAllByAssignment(any())).thenReturn(List.of());
    }

    private VehicleAssignment captureCreatedAssignment() {
        ArgumentCaptor<VehicleAssignment> captor = ArgumentCaptor.forClass(VehicleAssignment.class);
        verify(vehicleAssignmentService).save(captor.capture());
        return captor.getValue();
    }
}
