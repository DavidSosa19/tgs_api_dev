package com.example.tgs_dev.service;

import com.example.tgs_dev.entity.*;
import com.example.tgs_dev.repository.VehicleAssignmentRepository;
import com.example.tgs_dev.repository.specification.CommonSpecifications;
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

    public VehicleAssignmentService(VehicleAssignmentRepository vehicleAssignmentRepository) {
        this.vehicleAssignmentRepository = vehicleAssignmentRepository;
    }

    public VehicleAssignment save(VehicleAssignment vehicleAssignment) {
        return vehicleAssignmentRepository.save(vehicleAssignment);
    }

    public Optional<VehicleAssignment> findById(Integer id) {
        return vehicleAssignmentRepository.findById(id);
    }

    public List<VehicleAssignment> findAll() {
        return vehicleAssignmentRepository.findAll();
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
        List<VehicleAssignment> assignments = IntStream.range(0, dayRotation.size())
                .mapToObj(i -> {
                    RotationEntry entry = dayRotation.get(i);
                    return new VehicleAssignment(
                            routeOperation,
                            entry.getVehicle(),
                            entry.getScheduleTemplate(),
                            i + 1
                    );
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
