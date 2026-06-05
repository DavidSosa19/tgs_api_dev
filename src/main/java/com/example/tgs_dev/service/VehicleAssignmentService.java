package com.example.tgs_dev.service;

import com.example.tgs_dev.entity.*;
import com.example.tgs_dev.service.strategy.AssignmentSlot;
import com.example.tgs_dev.repository.VehicleAssignmentRepository;
import com.example.tgs_dev.repository.specification.CommonSpecifications;
import com.example.tgs_dev.repository.specification.TenantSpecifications;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

@Service
@Transactional(readOnly = true)
public class VehicleAssignmentService {

    private static final String DEFAULT_REMOVAL_REASON  = "REMOVED";
    private static final String FIELD_ROUTE_OPERATION   = "routeOperation";

    private final VehicleAssignmentRepository vehicleAssignmentRepository;
    private final TenantService               tenantService;

    public VehicleAssignmentService(VehicleAssignmentRepository vehicleAssignmentRepository,
                                    TenantService tenantService) {
        this.vehicleAssignmentRepository = vehicleAssignmentRepository;
        this.tenantService               = tenantService;
    }

    @Transactional
    public VehicleAssignment save(VehicleAssignment vehicleAssignment) {
        if (vehicleAssignment.getCompany() == null) {
            vehicleAssignment.setCompany(tenantService.currentCompany());
        }
        return vehicleAssignmentRepository.save(vehicleAssignment);
    }

    /**
     * Tenant-scoped lookup — prevents IDOR (CWE-639).
     * Only returns an assignment that belongs to the current tenant's company.
     */
    public Optional<VehicleAssignment> findById(Integer id) {
        Integer companyId = tenantService.currentCompanyId();
        return vehicleAssignmentRepository.findOne(
                Specification.<VehicleAssignment>where(CommonSpecifications.fieldEquals("id", id))
                        .and(TenantSpecifications.belongsToCompany(companyId)));
    }

    public List<VehicleAssignment> findAll() {
        return vehicleAssignmentRepository.findAll(
                TenantSpecifications.belongsToCompany(tenantService.currentCompanyId()));
    }

    public List<VehicleAssignment> findByRouteOperation(RouteOperation routeOperation) {
        return vehicleAssignmentRepository.findAll(
                CommonSpecifications.fieldEquals(FIELD_ROUTE_OPERATION, routeOperation));
    }

    @Transactional
    public void softDelete(VehicleAssignment assignment) {
        softDeleteWithReason(assignment, DEFAULT_REMOVAL_REASON);
    }

    @Transactional
    public void softDeleteWithReason(VehicleAssignment assignment, String reason) {
        assignment.setActive(false);
        assignment.setRemovedAt(LocalDateTime.now());
        assignment.setRemovalReason(reason);
        vehicleAssignmentRepository.save(assignment);
    }

    @Transactional
    public void softDeleteAll(List<VehicleAssignment> assignments) {
        if (assignments == null || assignments.isEmpty()) return;
        LocalDateTime now = LocalDateTime.now();
        assignments.forEach(a -> {
            a.setActive(false);
            a.setRemovedAt(now);
            a.setRemovalReason(DEFAULT_REMOVAL_REASON);
        });
        vehicleAssignmentRepository.saveAll(assignments);
    }

    /**
     * Creates one {@link VehicleAssignment} per slot in {@code slots}, preserving
     * order (index 0 → row-order 1).
     *
     * <p>Accepts {@code List<? extends AssignmentSlot>} so that any
     * {@link com.example.tgs_dev.service.strategy.ScheduleInitStrategy}
     * implementation can provide its own slot type without depending on
     * {@link RotationEntry}.
     *
     * @param slots          ordered list of vehicle-to-template slots
     * @param routeOperation the parent operation to link assignments to
     * @return the persisted assignments in the same order as {@code slots}
     */
    @Transactional
    public List<VehicleAssignment> assignVehicles(List<AssignmentSlot> slots,
                                                  RouteOperation routeOperation) {
        Company company = tenantService.currentCompany();
        List<VehicleAssignment> assignments = IntStream.range(0, slots.size())
                .mapToObj(i -> {
                    AssignmentSlot slot = slots.get(i);
                    VehicleAssignment va = new VehicleAssignment(
                            routeOperation,
                            slot.getVehicle(),
                            slot.getScheduleTemplate(),
                            i + 1
                    );
                    va.setCompany(company);
                    return va;
                })
                .toList();
        return vehicleAssignmentRepository.saveAll(assignments);
    }

    public List<VehicleAssignment> findByRouteOperationAndRowOrderGreaterThan(
            RouteOperation routeOperation, int rowOrder) {
        Specification<VehicleAssignment> byOperation =
                CommonSpecifications.fieldEquals(FIELD_ROUTE_OPERATION, routeOperation);
        Specification<VehicleAssignment> byRowOrder =
                CommonSpecifications.fieldGreaterThan("rowOrder", rowOrder);
        return vehicleAssignmentRepository.findAll(byOperation.and(byRowOrder));
    }

    public Optional<VehicleAssignment> findLastByRouteOperation(RouteOperation routeOperation) {
        return vehicleAssignmentRepository
                .findAll(CommonSpecifications.fieldEquals(FIELD_ROUTE_OPERATION, routeOperation))
                .stream()
                .max(Comparator.comparing(VehicleAssignment::getRowOrder));
    }

    /**
     * Returns all active assignments for the given operation with {@code vehicle}
     * and {@code scheduleTemplate} already loaded via a single JOIN FETCH —
     * eliminating N+1 lazy-load hits during serialisation.
     *
     * <p>The current tenant's {@code companyId} is passed as a second-level guard:
     * even if the caller has already validated that the {@link RouteOperation}
     * belongs to the current tenant, this clause guarantees no cross-tenant
     * assignment can ever leak through.
     *
     * <p>Results are ordered by {@code rowOrder ASC} — the canonical position
     * assigned during scheduling — so callers receive a deterministic sequence
     * without additional sorting.
     *
     * @param operation the parent route operation (must belong to the current tenant)
     * @return assignments ordered by row order, with vehicle and template pre-loaded
     */
    public List<VehicleAssignment> findByOperationWithDetails(RouteOperation operation) {
        return vehicleAssignmentRepository.findByOperationWithDetails(
                operation,
                tenantService.currentCompanyId()
        );
    }
}
