package com.example.tgs_dev.service;

import com.example.tgs_dev.controller.request.RemoveVehicleRequest;
import com.example.tgs_dev.entity.*;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class VehicleRemovalService {

    private final VehicleAssignmentService vehicleAssignmentService;
    private final ScheduleService scheduleService;
    private final RouteService routeService;
    private final RouteOperationService routeOperationService;

    public VehicleRemovalService(VehicleAssignmentService vehicleAssignmentService,
                                 ScheduleService scheduleService,
                                 RouteService routeService,
                                 RouteOperationService routeOperationService) {
        this.vehicleAssignmentService = vehicleAssignmentService;
        this.scheduleService = scheduleService;
        this.routeService = routeService;
        this.routeOperationService = routeOperationService;
    }

    @Transactional
    public void handleRemoval(RemoveVehicleRequest request) {
        VehicleAssignment assignment = vehicleAssignmentService.findById(request.vehicleAssignmentId())
                .orElseThrow(() -> new NoSuchElementException("notFound.vehicleAssignment|" + request.vehicleAssignmentId()));

        switch (request.removalType()) {
            case REMOVE_ONLY -> removeOnly(assignment);
            case REMOVE_RECALCULATE -> {
                if (request.effectiveFrom() == null) {
                    throw new IllegalArgumentException("effectiveFrom is required for REMOVE_RECALCULATE");
                }
                removeAndRecalculate(assignment, request.effectiveFrom());
            }
            case REMOVE_REPLACE -> removeAndReplace(assignment);
        }
    }

    /**
     * Soft-delete the assignment without any schedule recalculation.
     */
    private void removeOnly(VehicleAssignment assignment) {
        vehicleAssignmentService.softDelete(assignment);
    }

    /**
     * Soft-delete the assignment and redistribute the departure times of all
     * subsequent vehicles (rowOrder > removed) for schedules >= effectiveFrom.
     * Algorithm:
     *   T_removed = first departure time of the removed vehicle >= effectiveFrom
     *   T_last    = first departure time of the last remaining vehicle >= effectiveFrom
     *   interval  = (T_last - T_removed) / N_remaining
     *   new base for vehicle at index i = T_removed + interval * (i+1)
     */
    private void removeAndRecalculate(VehicleAssignment toRemove, LocalTime effectiveFrom) {
        RouteOperation routeOperation = toRemove.getRouteOperation();
        int removedRowOrder = toRemove.getRowOrder();

        // Load remaining assignments sorted by rowOrder BEFORE soft-delete
        List<VehicleAssignment> remaining = vehicleAssignmentService
                .findByRouteOperationAndRowOrderGreaterThan(routeOperation, removedRowOrder)
                .stream()
                .sorted(Comparator.comparing(VehicleAssignment::getRowOrder))
                .toList();

        if (remaining.isEmpty()) {
            vehicleAssignmentService.softDelete(toRemove);
            return;
        }

        // Load schedules BEFORE soft-delete — @SQLRestriction on VehicleAssignment
        // would filter them out via JOIN after the assignment is deactivated
        List<Schedule> removedSchedules = scheduleService
                .findAllByAssignment(List.of(toRemove.getId()))
                .stream()
                .filter(s -> !s.getDepartureTime().isBefore(effectiveFrom))
                .sorted(Comparator.comparing(Schedule::getDepartureTime))
                .toList();

        List<Integer> remainingIds = remaining.stream().map(VehicleAssignment::getId).toList();
        List<Schedule> allRemainingSchedules = scheduleService.findAllByAssignment(remainingIds);

        if (removedSchedules.isEmpty()) {
            vehicleAssignmentService.softDelete(toRemove);
            return;
        }

        LocalTime tRemoved = removedSchedules.getFirst().getDepartureTime();

        VehicleAssignment lastVehicle = remaining.getLast();
        LocalTime tLast = allRemainingSchedules.stream()
                .filter(s -> s.getVehicleAssignment().getId().equals(lastVehicle.getId()))
                .map(Schedule::getDepartureTime)
                .filter(departureTime -> !departureTime.isBefore(effectiveFrom))
                .min(Comparator.naturalOrder())
                .orElse(null);

        if (tLast == null) {
            vehicleAssignmentService.softDelete(toRemove);
            return;
        }

        long intervalMinutes = Duration.between(tRemoved, tLast).toMinutes() / remaining.size();

        // Soft-delete AFTER loading all data
        vehicleAssignmentService.softDelete(toRemove);

        // Redistribute departure times for remaining vehicles
        List<Schedule> toUpdate = new ArrayList<>();
        for (int i = 0; i < remaining.size(); i++) {
            VehicleAssignment va = remaining.get(i);
            LocalTime newBase = tRemoved.plusMinutes(intervalMinutes * (i + 1));

            List<Schedule> vehicleSchedules = allRemainingSchedules.stream()
                    .filter(s -> s.getVehicleAssignment().getId().equals(va.getId()))
                    .filter(s -> !s.getDepartureTime().isBefore(effectiveFrom))
                    .sorted(Comparator.comparing(Schedule::getDepartureTime))
                    .toList();

            if (vehicleSchedules.isEmpty()) continue;

            LocalTime oldBase = vehicleSchedules.getFirst().getDepartureTime();
            long shiftMinutes = Duration.between(oldBase, newBase).toMinutes();

            vehicleSchedules.forEach(s -> {
                s.setDepartureTime(s.getDepartureTime().plusMinutes(shiftMinutes));
                toUpdate.add(s);
            });
        }

        scheduleService.saveAll(toUpdate);
    }

    /**
     * Soft-delete the assignment and replace it with the last vehicle from route 3's
     * operation on the same date, generating schedules using the removed vehicle's template.
     */
    private void removeAndReplace(VehicleAssignment toRemove) {
        RouteOperation routeOperation = toRemove.getRouteOperation();
        LocalDate serviceDate = routeOperation.getServiceDate();

        Route route3 = routeService.findByNumber("3")
                .orElseThrow(() -> new NoSuchElementException("notFound.route|3"));

        RouteOperation route3Operation = routeOperationService
                .findByRouteAndDate(route3, serviceDate)
                .orElseThrow(() -> new NoSuchElementException("notFound.routeOperation.route3|" + serviceDate));

        VehicleAssignment lastRoute3Assignment = vehicleAssignmentService
                .findLastByRouteOperation(route3Operation)
                .orElseThrow(() -> new NoSuchElementException("notFound.vehicleAssignment.route3"));

        // Build replacement assignment with the loaned vehicle
        VehicleAssignment replacement = new VehicleAssignment(
                routeOperation,
                lastRoute3Assignment.getVehicle(),
                toRemove.getScheduleTemplate(),
                toRemove.getRowOrder()
        );
        replacement.setOrigin("REPLACEMENT");
        replacement.setReplacesId(toRemove.getId().longValue());

        VehicleAssignment savedReplacement = vehicleAssignmentService.save(replacement);

        // Generate schedules for the replacement vehicle
        scheduleService.calculateVehicleSchedules(List.of(savedReplacement));

        // Soft-delete the original and the route-3 assignment
        vehicleAssignmentService.softDeleteWithReason(toRemove, "REPLACED");
        vehicleAssignmentService.softDeleteWithReason(lastRoute3Assignment, "LOANED");
    }
}
