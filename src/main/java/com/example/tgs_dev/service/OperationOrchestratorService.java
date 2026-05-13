package com.example.tgs_dev.service;

import com.example.tgs_dev.entity.*;
import com.example.tgs_dev.entity.enums.ShiftDayType;
import com.example.tgs_dev.util.DateUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class OperationOrchestratorService {

    private final RouteOperationService routeOperationService;
    private final VehicleRotationService vehicleRotationService;
    private final VehicleAssignmentService vehicleAssignmentService;
    private final ScheduleService scheduleService;
    private final RouteService routeService;

    public OperationOrchestratorService(RouteOperationService routeOperationService,
                                        VehicleRotationService vehicleRotationService,
                                        VehicleAssignmentService vehicleAssignmentService,
                                        ScheduleService scheduleService, RouteService routeService) {
        this.routeOperationService = routeOperationService;
        this.vehicleRotationService = vehicleRotationService;
        this.vehicleAssignmentService = vehicleAssignmentService;
        this.scheduleService = scheduleService;
        this.routeService = routeService;
    }

    @Transactional
    public void initDailyOperation(Route route, List<RotationEntry> dayRotation,LocalDate date) {
        RouteOperation dayOperation = routeOperationService.initRoutOperation(route,date);
        List<VehicleAssignment> assignments = vehicleAssignmentService.assignVehicles(dayRotation, dayOperation);
        scheduleService.calculateVehicleSchedules(assignments);
    }

    public void initAllOperations(LocalDate date) {
        ShiftDayType dayType = DateUtils.getTypeofDay(date);
        Map<String, List<RotationEntry>> rotationMapForRoute = getRotationsByRoute(
                vehicleRotationService.getRotationFromDate(dayType, date));

        routeService.findAll().forEach(route ->
                initDailyOperation(route,
                        rotationMapForRoute.getOrDefault(route.getRouteNumber(), List.of()),date)
        );
    }

    public void initOperation(Route route, LocalDate date) {
        initDailyOperation(route, getEntriesForRoute(route, date),date);
    }

    private List<RotationEntry> getEntriesForRoute(Route route, LocalDate date) {
        ShiftDayType dayType = DateUtils.getTypeofDay(date);
        List<RotationEntry> dayRotation = vehicleRotationService.getRotationFromDate(dayType, date);
        return getRotationsByRoute(dayRotation)
                .getOrDefault(route.getRouteNumber(), List.of());
    }

    public Map<String, List<RotationEntry>> getRotationsByRoute(List<RotationEntry> rotations){
        return rotations.stream().
                collect(Collectors.groupingBy(
                        e->e.getScheduleTemplate().getRoute().getRouteNumber()));
    }
}
