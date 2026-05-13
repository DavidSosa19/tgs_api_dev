package com.example.tgs_dev.service;

import com.example.tgs_dev.entity.*;
import com.example.tgs_dev.repository.VehicleAssignmentRepository;
import com.example.tgs_dev.repository.specification.CommonSpecifications;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.IntStream;

@Service
public class VehicleAssignmentService {

    private final VehicleAssignmentRepository vehicleAssignmentRepository;
    private final VehicleService vehicleService;
    private final ScheduleTemplateService scheduleTemplateService;


    public VehicleAssignmentService(VehicleAssignmentRepository vehicleAssignmentRepository, VehicleService vehicleService, ScheduleTemplateService scheduleTemplateService) {
        this.vehicleAssignmentRepository = vehicleAssignmentRepository;
        this.vehicleService = vehicleService;
        this.scheduleTemplateService = scheduleTemplateService;
    }

    public VehicleAssignment save(VehicleAssignment vehicleAssignment){
        return vehicleAssignmentRepository.save(vehicleAssignment);
    }

    public Optional<VehicleAssignment> findById(Integer id){
        return vehicleAssignmentRepository.findById(id);
    }

    public List<VehicleAssignment> findAll(){
        return vehicleAssignmentRepository.findAll();
    }

    public List<VehicleAssignment> findByRouteOperation(RouteOperation routeOperation){
        return vehicleAssignmentRepository.findAll(CommonSpecifications.fieldEquals("routeOperation",routeOperation));
    }
    @Transactional
    public void softDelete(VehicleAssignment assignment) {
        assignment.setActive(false);
        assignment.setRemovedAt(LocalDateTime.now());
        assignment.setRemovalReason("REMOVED");
        vehicleAssignmentRepository.save(assignment);
    }

    @Transactional
    public void softDeleteAll(List<VehicleAssignment> assignments) {
        if (assignments == null || assignments.isEmpty()) return;
        LocalDateTime now = LocalDateTime.now();
        assignments.forEach(a -> {
            a.setActive(false);
            a.setRemovedAt(now);
            a.setRemovalReason("REMOVED");
        });
        vehicleAssignmentRepository.saveAll(assignments);
    }

    @Transactional
    public List<VehicleAssignment> assignVehicles(List<RotationEntry> dayRotation, RouteOperation routeOperation) {

        List<VehicleAssignment> assignments = IntStream.range(0, dayRotation.size())
                .mapToObj(i -> {
                    RotationEntry entry = dayRotation.get(i);
                    return new VehicleAssignment(
                            routeOperation,
                            entry.getVehicle(),
                            entry.getScheduleTemplate(),
                            i+1
                    );
                })
                .toList();

        return vehicleAssignmentRepository.saveAll(assignments);
    }
}
