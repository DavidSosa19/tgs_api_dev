package com.example.tgs_dev.service;

import com.example.tgs_dev.controller.response.AssignmentSchedulesDTO;
import com.example.tgs_dev.entity.RouteOperation;
import com.example.tgs_dev.entity.Schedule;
import com.example.tgs_dev.entity.VehicleAssignment;
import com.example.tgs_dev.repository.OperationEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class MatrixService {

    private final OperationEventRepository operationEventRepository;
    private final ScheduleService scheduleService;
    private final VehicleAssignmentService vehicleAssignmentService;
    private final RouteOperationService routeOperationService;

    public MatrixService(OperationEventRepository operationEventRepository,
                         ScheduleService scheduleService,
                         VehicleAssignmentService vehicleAssignmentService,
                         RouteOperationService routeOperationService) {
        this.operationEventRepository = operationEventRepository;
        this.scheduleService = scheduleService;
        this.vehicleAssignmentService = vehicleAssignmentService;
        this.routeOperationService = routeOperationService;
    }

    public List<VehicleAssignment> getAssignmentsByRoute(RouteOperation routeOperation){
        return vehicleAssignmentService.findByRouteOperation(routeOperation);
    }

    @Transactional(readOnly = true)
    public List<AssignmentSchedulesDTO> getAssignmentSchedules(Integer routeOperationId){
        RouteOperation routeOperation = routeOperationService.findById(routeOperationId);
        List<VehicleAssignment> assignments = getAssignmentsByRoute(routeOperation);
        if (assignments.isEmpty()) {
            return List.of();
        }
        List<Integer> ids = assignments.stream().map(VehicleAssignment::getId).toList();
        List<Schedule> schedules = scheduleService.findAllByAssignment(ids);
        Map<Integer, List<Schedule>> schedulesByAssignmentId = schedules.stream()
                .collect(Collectors.groupingBy(s -> s.getVehicleAssignment().getId()));
        return assignments.stream()
                .map(a -> new AssignmentSchedulesDTO(
                        a,
                        schedulesByAssignmentId.getOrDefault(a.getId(), List.of())
                ))
                .toList();
    }
}
