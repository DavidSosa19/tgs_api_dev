package com.example.tgs_dev.service;

import com.example.tgs_dev.controller.exception.BusinessException;
import com.example.tgs_dev.controller.request.RemoveVehicleRequest;
import com.example.tgs_dev.entity.RouteOperation;
import com.example.tgs_dev.entity.VehicleAssignment;
import com.example.tgs_dev.entity.enums.RemovalType;
import com.example.tgs_dev.service.removal.RemovalContext;
import com.example.tgs_dev.service.removal.RemovalOutcome;
import com.example.tgs_dev.service.removal.VehicleRemovalStrategy;
import com.example.tgs_dev.service.removal.VehicleRemovedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

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
@DisplayName("VehicleRemovalService")
class VehicleRemovalServiceTest {

    @Mock VehicleRemovalStrategy       removeOnlyStrategy;
    @Mock VehicleRemovalStrategy       recalcStrategy;
    @Mock VehicleAssignmentService     vehicleAssignmentService;
    @Mock RouteOperationService        routeOperationService;
    @Mock ApplicationEventPublisher    eventPublisher;

    private VehicleRemovalService sut;

    @BeforeEach
    void setUp() {
        when(removeOnlyStrategy.supports()).thenReturn(RemovalType.REMOVE_ONLY);
        when(recalcStrategy.supports()).thenReturn(RemovalType.REMOVE_RECALCULATE);
        sut = new VehicleRemovalService(
                List.of(removeOnlyStrategy, recalcStrategy),
                vehicleAssignmentService,
                routeOperationService,
                eventPublisher
        );
    }

    @Nested @DisplayName("guard clauses")
    class Guards {

        @Test @DisplayName("unknown assignment id → NoSuchElementException")
        void unknownId_throws() {
            when(vehicleAssignmentService.findById(99)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> sut.handleRemoval(request(99, RemovalType.REMOVE_ONLY)))
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessageContaining("99");

            verifyNoInteractions(eventPublisher);
        }

        @Test @DisplayName("removal type with no registered strategy → BusinessException")
        void unsupportedRemovalType_throws() {
            VehicleAssignment va = simpleAssignment();
            when(vehicleAssignmentService.findById(1)).thenReturn(Optional.of(va));
            stubLock(va);

            assertThatThrownBy(() -> sut.handleRemoval(request(1, RemovalType.REMOVE_REPLACE)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("unsupported.removalType");

            verify(removeOnlyStrategy, never()).execute(any());
            verify(recalcStrategy,     never()).execute(any());
            verifyNoInteractions(eventPublisher);
        }
    }

    @Nested @DisplayName("dispatch & lock")
    class Dispatch {

        @Test @DisplayName("acquires pessimistic write lock on the parent operation")
        void acquiresLock() {
            VehicleAssignment va = simpleAssignment();
            when(vehicleAssignmentService.findById(1)).thenReturn(Optional.of(va));
            stubLock(va);
            when(removeOnlyStrategy.execute(any())).thenReturn(RemovalOutcome.empty());

            sut.handleRemoval(request(1, RemovalType.REMOVE_ONLY));

            verify(routeOperationService).findByIdForUpdate(va.getRouteOperation().getId());
        }

        @Test @DisplayName("dispatches to REMOVE_ONLY strategy")
        void dispatchesToRemoveOnly() {
            VehicleAssignment va = simpleAssignment();
            when(vehicleAssignmentService.findById(1)).thenReturn(Optional.of(va));
            stubLock(va);
            when(removeOnlyStrategy.execute(any())).thenReturn(RemovalOutcome.empty());

            sut.handleRemoval(request(1, RemovalType.REMOVE_ONLY));

            verify(removeOnlyStrategy).execute(any());
            verify(recalcStrategy, never()).execute(any());
        }

        @Test @DisplayName("builds RemovalContext with the request fields and a fresh now")
        void buildsContextFromRequest() {
            VehicleAssignment va = simpleAssignment();
            when(vehicleAssignmentService.findById(1)).thenReturn(Optional.of(va));
            stubLock(va);
            when(removeOnlyStrategy.execute(any())).thenReturn(RemovalOutcome.empty());

            var req = new RemoveVehicleRequest(1, RemovalType.REMOVE_ONLY, LocalTime.of(9, 0),
                                                null, null, null);
            sut.handleRemoval(req);

            ArgumentCaptor<RemovalContext> captor = ArgumentCaptor.forClass(RemovalContext.class);
            verify(removeOnlyStrategy).execute(captor.capture());

            RemovalContext ctx = captor.getValue();
            assertThat(ctx.assignment()).isEqualTo(va);
            assertThat(ctx.fromTime()).isEqualTo(LocalTime.of(9, 0));
            assertThat(ctx.now()).isNotNull();
        }
    }

    @Nested @DisplayName("event emission")
    class EventEmission {

        @Test @DisplayName("emits VehicleRemovedEvent after successful removal")
        void emitsEventAfterSuccess() {
            VehicleAssignment va = simpleAssignment();
            when(vehicleAssignmentService.findById(1)).thenReturn(Optional.of(va));
            stubLock(va);
            when(removeOnlyStrategy.execute(any())).thenReturn(RemovalOutcome.empty());

            sut.handleRemoval(request(1, RemovalType.REMOVE_ONLY));

            ArgumentCaptor<VehicleRemovedEvent> captor = ArgumentCaptor.forClass(VehicleRemovedEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());

            VehicleRemovedEvent event = captor.getValue();
            assertThat(event.assignmentId()).isEqualTo(va.getId());
            assertThat(event.routeOperationId()).isEqualTo(va.getRouteOperation().getId());
            assertThat(event.removalType()).isEqualTo(RemovalType.REMOVE_ONLY);
            assertThat(event.replacementId()).isNull();
            assertThat(event.occurredAt()).isNotNull();
        }

        @Test @DisplayName("event includes replacementId when strategy returns one")
        void eventIncludesReplacementId() {
            VehicleAssignment va = simpleAssignment();
            when(vehicleAssignmentService.findById(1)).thenReturn(Optional.of(va));
            stubLock(va);
            when(removeOnlyStrategy.execute(any())).thenReturn(RemovalOutcome.withReplacement(42));

            sut.handleRemoval(request(1, RemovalType.REMOVE_ONLY));

            ArgumentCaptor<VehicleRemovedEvent> captor = ArgumentCaptor.forClass(VehicleRemovedEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());
            assertThat(captor.getValue().replacementId()).isEqualTo(42);
        }

        @Test @DisplayName("event NOT emitted when strategy throws")
        void eventNotEmittedOnFailure() {
            VehicleAssignment va = simpleAssignment();
            when(vehicleAssignmentService.findById(1)).thenReturn(Optional.of(va));
            stubLock(va);
            when(removeOnlyStrategy.execute(any())).thenThrow(new RuntimeException("boom"));

            assertThatThrownBy(() -> sut.handleRemoval(request(1, RemovalType.REMOVE_ONLY)))
                    .isInstanceOf(RuntimeException.class);

            verifyNoInteractions(eventPublisher);
        }
    }

    // ── Shared helpers ─────────────────────────────────────────────────────────

    private static VehicleAssignment simpleAssignment() {
        return assignment(1, operation(1, route(1, "1"), OP_DATE),
                          vehicle(10, "V-001"), template(100, route(1, "1"), 1), 1);
    }

    private void stubLock(VehicleAssignment va) {
        RouteOperation op = va.getRouteOperation();
        lenient().when(routeOperationService.findByIdForUpdate(op.getId())).thenReturn(op);
    }

    private static RemoveVehicleRequest request(int assignmentId, RemovalType type) {
        return new RemoveVehicleRequest(assignmentId, type, LocalTime.of(9, 0), null, null, null);
    }
}
