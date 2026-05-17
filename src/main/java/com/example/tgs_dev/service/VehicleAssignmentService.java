package com.example.tgs_dev.service;

import com.example.tgs_dev.entity.*;
import com.example.tgs_dev.repository.VehicleAssignmentRepository;
import com.example.tgs_dev.repository.specification.CommonSpecifications;
import com.example.tgs_dev.repository.specification.TenantSpecifications;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

@Service
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

    @Transactional
    public List<VehicleAssignment> assignVehicles(List<RotationEntry> dayRotation,
                                                  RouteOperation routeOperation) {
        Company company = tenantService.currentCompany();
        List<VehicleAssignment> assignments = IntStream.range(0, dayRotation.size())
                .mapToObj(i -> {
                    RotationEntry entry = dayRotation.get(i);
                    VehicleAssignment va = new VehicleAssignment(
                            routeOperation,
                            entry.getVehicle(),
                            entry.getScheduleTemplate(),
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
}
