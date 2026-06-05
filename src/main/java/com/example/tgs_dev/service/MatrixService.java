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
import com.example.tgs_dev.entity.enums.ScheduleOrigin;
import com.example.tgs_dev.repository.VehicleAssignmentRepository;
import com.example.tgs_dev.repository.projection.ScheduleProjection;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class MatrixService {

    private final ScheduleService             scheduleService;
    private final VehicleAssignmentService    vehicleAssignmentService;
    private final VehicleAssignmentRepository vehicleAssignmentRepository;
    private final RouteOperationService       routeOperationService;
    private final TenantService               tenantService;

    public MatrixService(ScheduleService scheduleService,
                         VehicleAssignmentService vehicleAssignmentService,
                         VehicleAssignmentRepository vehicleAssignmentRepository,
                         RouteOperationService routeOperationService,
                         TenantService tenantService) {
        this.scheduleService             = scheduleService;
        this.vehicleAssignmentService    = vehicleAssignmentService;
        this.vehicleAssignmentRepository = vehicleAssignmentRepository;
        this.routeOperationService       = routeOperationService;
        this.tenantService               = tenantService;
    }

    /**
     * Returns the <strong>operational</strong> schedule view: only active
     * vehicles and their currently-effective schedules.  Each entry carries
     * lifecycle metadata ({@code active}, {@code origin},
     * {@code originalDepartureTime}, {@code deltaMinutesFromOriginal},
     * {@code supersededReason}) so the front can render diffs without
     * additional queries.
     *
     * <p>Three SQL queries fire: operation lookup, active VAs with vehicle and
     * template (JOIN FETCH), and the batch projection of active schedules.
     */
    @Transactional(readOnly = true)
    public OperationScheduleDTO getOperationSchedules(Integer routeOperationId) {
        RouteOperation     operation = routeOperationService.findById(routeOperationId);
        OperationHeaderDTO header    = OperationHeaderDTO.from(operation);

        List<VehicleAssignment> assignments =
                vehicleAssignmentService.findByOperationWithDetails(operation);

        if (assignments.isEmpty()) {
            return OperationScheduleDTO.empty(header);
        }

        List<Integer> assignmentIds = assignments.stream()
                .map(VehicleAssignment::getId)
                .toList();

        List<ScheduleProjection> projections =
                scheduleService.findScheduleProjections(assignmentIds);

        return assembleResponse(header, assignments, projections);
    }

    /**
     * Returns the <strong>original-plan</strong> view: every assignment that
     * has ever existed in the operation (including soft-deleted ones), each
     * paired with its {@link ScheduleOrigin#ORIGINAL} schedules regardless of
     * whether they are currently active.
     *
     * <p>This is the audit perspective — "what was originally planned at
     * operation init".  Inactive assignments (vehicles that were removed or
     * replaced after init) are surfaced for visual integrity with their
     * {@code active=false}, {@code removalReason}, {@code removedAt} fields
     * populated.
     *
     * <p>Uses native queries to bypass the {@code @SQLRestriction} on
     * {@link VehicleAssignment} — see
     * {@link VehicleAssignmentRepository#findAllByOperationIncludingInactive}.
     */
    @Transactional(readOnly = true)
    public OperationScheduleDTO getOriginalOperationSchedules(Integer routeOperationId) {
        RouteOperation     operation = routeOperationService.findById(routeOperationId);
        OperationHeaderDTO header    = OperationHeaderDTO.from(operation);

        List<VehicleAssignment> assignments = vehicleAssignmentRepository
                .findAllByOperationIncludingInactive(routeOperationId,
                                                      tenantService.currentCompanyId());
        if (assignments.isEmpty()) return OperationScheduleDTO.empty(header);

        List<Integer> assignmentIds = assignments.stream()
                .map(VehicleAssignment::getId)
                .toList();

        List<ScheduleProjection> projections =
                scheduleService.findOriginalProjections(assignmentIds);

        return assembleResponse(header, assignments, projections);
    }

    // ── Common assembly path ──────────────────────────────────────────────────

    private OperationScheduleDTO assembleResponse(OperationHeaderDTO header,
                                                   List<VehicleAssignment> assignments,
                                                   List<ScheduleProjection> projections) {
        Map<Integer, List<ScheduleProjection>> byAssignment = projections.stream()
                .collect(Collectors.groupingBy(
                        ScheduleProjection::getAssignmentId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<VehicleScheduleDTO> vehicleSchedules = assignments.stream()
                .map(va -> {
                    List<ScheduleEntryDTO> entries = byAssignment
                            .getOrDefault(va.getId(), List.of())
                            .stream()
                            .map(MatrixService::toScheduleEntry)
                            .toList();
                    return new VehicleScheduleDTO(
                            va.getId(),
                            va.getRowOrder(),
                            Boolean.TRUE.equals(va.getActive()),
                            va.getRemovalReason(),
                            va.getRemovedAt(),
                            VehicleInfoDTO.from(va.getVehicle()),
                            TemplateInfoDTO.from(va.getScheduleTemplate()),
                            entries
                    );
                })
                .toList();

        return new OperationScheduleDTO(header, vehicleSchedules);
    }

    /**
     * Maps a {@link ScheduleProjection} row into a {@link ScheduleEntryDTO},
     * resolving the enum for {@code origin} and computing
     * {@code deltaMinutesFromOriginal} so the front does not have to.
     */
    private static ScheduleEntryDTO toScheduleEntry(ScheduleProjection p) {
        ScheduleOrigin origin       = p.getOrigin() != null ? ScheduleOrigin.valueOf(p.getOrigin()) : null;
        LocalTime      originalTime = p.getOriginalDepartureTime();
        Long           delta        = (originalTime != null)
                ? Duration.between(originalTime, p.getDepartureTime()).toMinutes()
                : null;
        return new ScheduleEntryDTO(
                p.getScheduleId(),
                p.getDepartureOrder(),
                p.getTripNumber(),
                p.getDepartureTime(),
                originalTime,
                delta,
                Boolean.TRUE.equals(p.getActive()),
                origin,
                p.getSupersededReason()
        );
    }

    // ── Legacy / deprecated methods ───────────────────────────────────────────

    public List<VehicleAssignment> getAssignmentsByRoute(RouteOperation routeOperation){
        return vehicleAssignmentService.findByRouteOperation(routeOperation);
    }

    @Transactional(readOnly = true)
    public List<AssignmentSchedulesDTO> getAssignmentSchedules(Integer routeOperationId){
        RouteOperation routeOperation = routeOperationService.findById(routeOperationId);
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
