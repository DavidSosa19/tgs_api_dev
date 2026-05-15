package com.example.tgs_dev.service;

import com.example.tgs_dev.TestFixtures;
import com.example.tgs_dev.entity.*;
import com.example.tgs_dev.repository.VehicleAssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.jpa.domain.Specification;

import java.time.LocalTime;
import java.util.List;

import static com.example.tgs_dev.TestFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for VehicleAssignmentService.
 *
 * Strategy: only test methods that contain logic (state transitions, invariants,
 * transformations). Pure delegation methods (save → repo.save) are intentionally
 * omitted — they add no value and couple tests to implementation details.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("VehicleAssignmentService")
class VehicleAssignmentServiceTest {

    @Mock VehicleAssignmentRepository repo;

    @InjectMocks VehicleAssignmentService sut;

    private Route            route;
    private RouteOperation   op;
    private Vehicle          vehicle;
    private ScheduleTemplate template;

    @BeforeEach
    void setUp() {
        route    = route(1, "1");
        op       = operation(1, route, OP_DATE);
        vehicle  = vehicle(10, "V-001");
        template = template(100, route, LocalTime.of(6, 0));
    }

    // ── softDelete ─────────────────────────────────────────────────────────────
    @Nested @DisplayName("softDelete")
    class SoftDelete {

        @Test @DisplayName("marks assignment inactive and records removal metadata")
        void marksInactiveWithMetadata() {
            VehicleAssignment va = assignment(1, op, vehicle, template, 1);

            sut.softDelete(va);

            assertThat(va.getActive()).isFalse();
            assertThat(va.getRemovedAt()).isNotNull();
            assertThat(va.getRemovalReason()).isEqualTo("REMOVED");
        }

        @Test @DisplayName("persists the state change")
        void persists() {
            VehicleAssignment va = assignment(1, op, vehicle, template, 1);
            sut.softDelete(va);
            verify(repo).save(va);
        }
    }

    // ── softDeleteWithReason ───────────────────────────────────────────────────
    @Nested @DisplayName("softDeleteWithReason")
    class SoftDeleteWithReason {

        @Test @DisplayName("uses the supplied reason instead of the default")
        void customReasonIsApplied() {
            VehicleAssignment va = assignment(1, op, vehicle, template, 1);

            sut.softDeleteWithReason(va, "LOANED");

            assertThat(va.getActive()).isFalse();
            assertThat(va.getRemovalReason()).isEqualTo("LOANED");
        }

        @Test @DisplayName("'REPLACED' reason is stored when original is swapped out")
        void replacedReason() {
            VehicleAssignment va = assignment(1, op, vehicle, template, 1);
            sut.softDeleteWithReason(va, "REPLACED");
            assertThat(va.getRemovalReason()).isEqualTo("REPLACED");
        }
    }

    // ── softDeleteAll ──────────────────────────────────────────────────────────
    @Nested @DisplayName("softDeleteAll")
    class SoftDeleteAll {

        @Test @DisplayName("null or empty list is a no-op")
        void nullOrEmpty_doesNothing() {
            sut.softDeleteAll(null);
            sut.softDeleteAll(List.of());
            verifyNoInteractions(repo);
        }

        @Test @DisplayName("every assignment in the list is marked inactive")
        void allMarkedInactive() {
            VehicleAssignment va1 = assignment(1, op, vehicle, template, 1);
            VehicleAssignment va2 = assignment(2, op, vehicle, template, 2);

            sut.softDeleteAll(List.of(va1, va2));

            assertThat(va1.getActive()).isFalse();
            assertThat(va2.getActive()).isFalse();
        }

        @Test @DisplayName("all assignments share the same removedAt timestamp (batch consistency)")
        void batchConsistency_sameTimestamp() {
            VehicleAssignment va1 = assignment(1, op, vehicle, template, 1);
            VehicleAssignment va2 = assignment(2, op, vehicle, template, 2);

            sut.softDeleteAll(List.of(va1, va2));

            // Both captures the same LocalDateTime instance — not two separate now() calls
            assertThat(va1.getRemovedAt()).isNotNull().isEqualTo(va2.getRemovedAt());
        }
    }

    // ── assignVehicles ─────────────────────────────────────────────────────────
    @Nested @DisplayName("assignVehicles")
    class AssignVehicles {

        @Test @DisplayName("row order is 1-based and matches the entry index")
        void rowOrderIsOneBased() {
            Vehicle v2 = vehicle(20, "V-002");
            List<RotationEntry> entries = List.of(entry(vehicle, template), entry(v2, template));
            when(repo.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

            sut.assignVehicles(entries, op);

            ArgumentCaptor<List<VehicleAssignment>> captor = ArgumentCaptor.forClass(List.class);
            verify(repo).saveAll(captor.capture());
            List<VehicleAssignment> created = captor.getValue();
            assertThat(created.get(0).getRowOrder()).isEqualTo(1);
            assertThat(created.get(1).getRowOrder()).isEqualTo(2);
        }

        @Test @DisplayName("each assignment is linked to the correct vehicle from its entry")
        void vehiclesArePreserved() {
            Vehicle v2 = vehicle(20, "V-002");
            when(repo.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

            sut.assignVehicles(List.of(entry(vehicle, template), entry(v2, template)), op);

            ArgumentCaptor<List<VehicleAssignment>> captor = ArgumentCaptor.forClass(List.class);
            verify(repo).saveAll(captor.capture());
            assertThat(captor.getValue().get(0).getVehicle()).isEqualTo(vehicle);
            assertThat(captor.getValue().get(1).getVehicle()).isEqualTo(v2);
        }

        @Test @DisplayName("empty entry list produces no assignments")
        void emptyEntries_producesNoAssignments() {
            when(repo.saveAll(any())).thenReturn(List.of());
            assertThat(sut.assignVehicles(List.of(), op)).isEmpty();
        }
    }

    // ── findLastByRouteOperation ───────────────────────────────────────────────
    @Nested @DisplayName("findLastByRouteOperation")
    class FindLast {

        @Test @DisplayName("returns the assignment with the highest rowOrder")
        void returnsHighestRowOrder() {
            VehicleAssignment first  = assignment(1, op, vehicle, template, 1);
            VehicleAssignment middle = assignment(2, op, vehicle, template, 2);
            VehicleAssignment last   = assignment(3, op, vehicle, template, 5);
            when(repo.findAll(any(Specification.class))).thenReturn(List.of(first, last, middle)); // intentionally unordered

            assertThat(sut.findLastByRouteOperation(op))
                    .isPresent()
                    .hasValueSatisfying(va -> assertThat(va.getRowOrder()).isEqualTo(5));
        }

        @Test @DisplayName("returns empty when the operation has no assignments")
        void noAssignments_returnsEmpty() {
            when(repo.findAll(any(Specification.class))).thenReturn(List.of());
            assertThat(sut.findLastByRouteOperation(op)).isEmpty();
        }
    }
}
