package com.example.tgs_dev.service;

import com.example.tgs_dev.controller.response.AssignmentSchedulesDTO;
import com.example.tgs_dev.controller.response.viewer.OperationHeaderDTO;
import com.example.tgs_dev.controller.response.viewer.OperationScheduleDTO;
import com.example.tgs_dev.controller.response.viewer.ScheduleEntryDTO;
import com.example.tgs_dev.controller.response.viewer.TemplateInfoDTO;
import com.example.tgs_dev.controller.response.viewer.VehicleInfoDTO;
import com.example.tgs_dev.controller.response.viewer.VehicleScheduleDTO;
import com.example.tgs_dev.entity.RouteOperation;
import com.example.tgs_dev.entity.Schedule;
import com.example.tgs_dev.entity.VehicleAssignment;
import com.example.tgs_dev.repository.projection.ScheduleProjection;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class MatrixService {

    private final ScheduleService scheduleService;
    private final VehicleAssignmentService vehicleAssignmentService;
    private final RouteOperationService routeOperationService;

    public MatrixService(ScheduleService scheduleService,
                         VehicleAssignmentService vehicleAssignmentService,
                         RouteOperationService routeOperationService) {
        this.scheduleService = scheduleService;
        this.vehicleAssignmentService = vehicleAssignmentService;
        this.routeOperationService = routeOperationService;
    }

    /**
     * Returns the full schedule for a route operation, structured for direct
     * frontend rendering.
     *
     * <p>The implementation fires exactly <strong>three SQL queries</strong>:
     * <ol>
     *   <li>Tenant-scoped lookup of the {@link RouteOperation} (includes route
     *       via lazy load — resolved within this transaction).</li>
     *   <li>JOIN FETCH of all active {@link VehicleAssignment}s with their
     *       {@code vehicle} and {@code scheduleTemplate} pre-loaded.</li>
     *   <li>Batch projection fetch of all {@link Schedule} rows for the
     *       assignment IDs gathered in step 2.</li>
     * </ol>
     *
     * <p><strong>Redis caching hook</strong> — this method is the designated
     * hook point. When caching is added, annotate with
     * {@code @Cacheable(value = "operationSchedule", key = "#routeOperationId + ':' + T(com.example.tgs_dev.security.TenantContext).getCompanyId()")}
     * and ensure the cache is evicted on any write to the operation's assignments
     * or schedules. The returned {@link OperationScheduleDTO} is intentionally
     * serialization-friendly (all fields are primitives, {@link java.time.LocalDate},
     * {@link java.time.LocalTime}, or nested records) so it can be stored as JSON
     * or with Kryo without structural changes.
     *
     * @param routeOperationId the ID of the operation to fetch
     * @return the full schedule DTO, or an empty schedule if no assignments exist
     * @throws java.util.NoSuchElementException if the operation is not found for
     *                                          the current tenant
     */
    @Transactional(readOnly = true)
    public OperationScheduleDTO getOperationSchedules(Integer routeOperationId) {
        // Query 1 — tenant-scoped operation lookup; route lazy-loaded in this tx
        RouteOperation operation = routeOperationService.findById(routeOperationId);
        OperationHeaderDTO header = OperationHeaderDTO.from(operation);

        // Query 2 — assignments with vehicle + template pre-loaded (JOIN FETCH)
        List<VehicleAssignment> assignments =
                vehicleAssignmentService.findByOperationWithDetails(operation);

        if (assignments.isEmpty()) {
            return OperationScheduleDTO.empty(header);
        }

        // Query 3 — batch projection fetch; ordered by assignmentId, departureOrder
        List<Integer> assignmentIds = assignments.stream()
                .map(VehicleAssignment::getId)
                .toList();
        List<ScheduleProjection> projections =
                scheduleService.findScheduleProjections(assignmentIds);

        // Group projections by assignmentId preserving insertion (= sorted) order
        Map<Integer, List<ScheduleProjection>> byAssignment = projections.stream()
                .collect(Collectors.groupingBy(
                        ScheduleProjection::getAssignmentId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        // Map assignments → VehicleScheduleDTOs (rowOrder ordering preserved by query)
        List<VehicleScheduleDTO> vehicleSchedules = assignments.stream()
                .map(va -> {
                    List<ScheduleEntryDTO> entries = byAssignment
                            .getOrDefault(va.getId(), List.of())
                            .stream()
                            .map(p -> new ScheduleEntryDTO(p.getDepartureOrder(), p.getDepartureTime()))
                            .toList();

                    return new VehicleScheduleDTO(
                            va.getId(),
                            va.getRowOrder(),
                            VehicleInfoDTO.from(va.getVehicle()),
                            TemplateInfoDTO.from(va.getScheduleTemplate()),
                            entries
                    );
                })
                .toList();

        return new OperationScheduleDTO(header, vehicleSchedules);
    }

    public List<VehicleAssignment> getAssignmentsByRoute(RouteOperation routeOperation){
        return vehicleAssignmentService.findByRouteOperation(routeOperation);
    }

    @Transactional(readOnly = true)
    public List<AssignmentSchedulesDTO> getAssignmentSchedules(Integer routeOperationId){
        RouteOperation routeOperation = routeOperationService.findById(routeOperationId);
        // Use findByOperationWithDetails (JOIN FETCH vehicle) so the Vehicle proxy is
        // initialized before the session closes; otherwise Jackson hits a
        // LazyInitializationException when serializing VehicleAssignment.vehicle.
        List<VehicleAssignment> assignments =
                vehicleAssignmentService.findByOperationWithDetails(routeOperation);
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
