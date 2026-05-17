package com.example.tgs_dev.service;

import com.example.tgs_dev.entity.Company;
import com.example.tgs_dev.entity.Route;
import com.example.tgs_dev.entity.RouteOperation;
import com.example.tgs_dev.entity.VehicleAssignment;
import com.example.tgs_dev.repository.RouteOperationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static com.example.tgs_dev.TestFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RouteOperationService.
 *
 * Delegates (findAll, findAllByDate) are omitted.
 * Focus: exception contract on findById, cascade invariant on soft-delete,
 * entity construction in initRoutOperation.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RouteOperationService")
class RouteOperationServiceTest {

    @Mock RouteOperationRepository repo;
    @Mock VehicleAssignmentService vehicleAssignmentService;
    @Mock TenantService            tenantService;

    @InjectMocks RouteOperationService sut;

    private static final int     COMPANY_ID = 1;
    private static final Company COMPANY    = company(COMPANY_ID, "Test Corp");

    private Route          route;
    private RouteOperation op;

    @BeforeEach
    void setUp() {
        route = route(1, "1");
        op    = operation(1, route, OP_DATE);
        lenient().when(tenantService.currentCompanyId()).thenReturn(COMPANY_ID);
        lenient().when(tenantService.currentCompany()).thenReturn(COMPANY);
    }

    // ── findById ──────────────────────────────────────────────────────────────
    @Nested @DisplayName("findById")
    class FindById {

        @Test @DisplayName("returns entity when present")
        void returnsWhenFound() {
            when(repo.findOne(any(Specification.class))).thenReturn(Optional.of(op));
            assertThat(sut.findById(1)).isSameAs(op);
        }

        @Test @DisplayName("throws NoSuchElementException with the id in the message")
        void throwsWithId() {
            when(repo.findOne(any(Specification.class))).thenReturn(Optional.empty());
            assertThatThrownBy(() -> sut.findById(99))
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessageContaining("99");
        }
    }

    // ── softDelete ─────────────────────────────────────────────────────────────
    @Nested @DisplayName("softDelete (single operation)")
    class SoftDelete {

        @Test @DisplayName("soft-deletes all assignments before deactivating the operation")
        void cascadeOrderIsAssignmentsFirst() {
            VehicleAssignment va = assignment(1, op, vehicle(10, "V"), template(100, route, null), 1);
            when(vehicleAssignmentService.findByRouteOperation(op)).thenReturn(List.of(va));

            sut.softDelete(op);

            var inOrder = inOrder(vehicleAssignmentService, repo);
            inOrder.verify(vehicleAssignmentService).softDeleteAll(List.of(va));
            inOrder.verify(repo).softDelete(op);
        }

        @Test @DisplayName("operation with no assignments is still soft-deleted")
        void noAssignments_operationIsStillDeleted() {
            when(vehicleAssignmentService.findByRouteOperation(op)).thenReturn(List.of());

            sut.softDelete(op);

            verify(repo).softDelete(op);
        }
    }

    // ── softDeleteAllByDate ───────────────────────────────────────────────────
    @Nested @DisplayName("softDeleteAllByDate")
    class SoftDeleteAllByDate {

        @Test @DisplayName("no-op when there are no operations for the date")
        void noOperations_doesNothing() {
            when(repo.findAll(any(Specification.class))).thenReturn(List.of());

            sut.softDeleteAllByDate(OP_DATE);

            verifyNoInteractions(vehicleAssignmentService);
            verify(repo, never()).softDeleteAll(any());
        }

        @Test @DisplayName("cascades assignment deletion for each operation")
        void cascadesAssignmentDeletionForAll() {
            RouteOperation op2 = operation(2, route, OP_DATE);
            VehicleAssignment va1 = assignment(1, op,  vehicle(10, "V1"), template(100, route, null), 1);
            VehicleAssignment va2 = assignment(2, op2, vehicle(20, "V2"), template(100, route, null), 1);

            when(repo.findAll(any(Specification.class))).thenReturn(List.of(op, op2));
            when(vehicleAssignmentService.findByRouteOperation(op)).thenReturn(List.of(va1));
            when(vehicleAssignmentService.findByRouteOperation(op2)).thenReturn(List.of(va2));

            sut.softDeleteAllByDate(OP_DATE);

            verify(vehicleAssignmentService).softDeleteAll(List.of(va1));
            verify(vehicleAssignmentService).softDeleteAll(List.of(va2));
            verify(repo).softDeleteAll(List.of(op, op2));
        }
    }

    // ── initRoutOperation ─────────────────────────────────────────────────────
    @Nested @DisplayName("initRoutOperation")
    class InitRoutOperation {

        @Test @DisplayName("creates a RouteOperation with correct route, date and company")
        void createsWithCorrectFields() {
            LocalDate targetDate = LocalDate.of(2024, 3, 10);
            when(repo.save(any(RouteOperation.class))).thenAnswer(inv -> {
                RouteOperation created = inv.getArgument(0);
                created.setId(42);
                return created;
            });

            RouteOperation result = sut.initRoutOperation(route, targetDate);

            assertThat(result.getRoute()).isEqualTo(route);
            assertThat(result.getServiceDate()).isEqualTo(targetDate);
            assertThat(result.getCompany()).isEqualTo(COMPANY);
        }
    }
}
