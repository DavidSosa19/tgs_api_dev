package com.example.tgs_dev.controller;

import com.example.tgs_dev.controller.request.ScheduleTemplateRequest;
import com.example.tgs_dev.controller.response.ApiResponse;
import com.example.tgs_dev.controller.response.ScheduleTemplateDTO;
import com.example.tgs_dev.entity.Route;
import com.example.tgs_dev.mapper.ScheduleTemplateMapper;
import com.example.tgs_dev.repository.filter.FilterRequest;
import com.example.tgs_dev.security.Permissions;
import com.example.tgs_dev.service.RouteService;
import com.example.tgs_dev.service.ScheduleTemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST endpoints for {@link com.example.tgs_dev.entity.ScheduleTemplate}.
 *
 * <p>Path-variable {@code groupId} is the SCD stable business identity
 * ({@code schedule_template_group.id}).  {@code routeId} / {@code secondaryRouteId}
 * in the request body are {@code route_group.id} values, resolved here to the
 * current active {@link Route} version before delegating to the service.
 */
@RestController
@RequestMapping("/api/scheduleTemplate")
@RequiredArgsConstructor
public class ScheduleTemplateController {

    private final ScheduleTemplateService scheduleTemplateService;
    private final RouteService            routeService;
    private final ScheduleTemplateMapper  scheduleTemplateMapper;

    @GetMapping
    @PreAuthorize("hasAuthority('" + Permissions.SCHEDULE_TEMPLATE_READ + "')")
    public ResponseEntity<ApiResponse<List<ScheduleTemplateDTO>>> getAll() {
        List<ScheduleTemplateDTO> templates =
                scheduleTemplateMapper.toDTOList(scheduleTemplateService.findAll());
        return ResponseEntity.ok(ApiResponse.ok(templates));
    }

    @GetMapping("/{groupId}")
    @PreAuthorize("hasAuthority('" + Permissions.SCHEDULE_TEMPLATE_READ + "')")
    public ResponseEntity<ApiResponse<ScheduleTemplateDTO>> getById(@PathVariable Long groupId) {
        ScheduleTemplateDTO dto = scheduleTemplateMapper.toDTO(scheduleTemplateService.findByGroupId(groupId));
        return ResponseEntity.ok(ApiResponse.ok(dto));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + Permissions.SCHEDULE_TEMPLATE_WRITE + "')")
    public ResponseEntity<ApiResponse<ScheduleTemplateDTO>> create(@RequestBody @Valid ScheduleTemplateRequest request) {
        Route route          = routeService.findByGroupId(request.routeId());
        Route secondaryRoute = request.secondaryRouteId() != null
                ? routeService.findByGroupId(request.secondaryRouteId())
                : null;
        ScheduleTemplateDTO dto = scheduleTemplateMapper.toDTO(
                scheduleTemplateService.create(request, route, secondaryRoute));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Template created successfully", dto));
    }

    @PutMapping("/{groupId}")
    @PreAuthorize("hasAuthority('" + Permissions.SCHEDULE_TEMPLATE_WRITE + "')")
    public ResponseEntity<ApiResponse<ScheduleTemplateDTO>> update(@PathVariable Long groupId,
                                                                   @RequestBody @Valid ScheduleTemplateRequest request) {
        Route route          = routeService.findByGroupId(request.routeId());
        Route secondaryRoute = request.secondaryRouteId() != null
                ? routeService.findByGroupId(request.secondaryRouteId())
                : null;
        ScheduleTemplateDTO dto = scheduleTemplateMapper.toDTO(
                scheduleTemplateService.update(groupId, request, route, secondaryRoute));
        return ResponseEntity.ok(ApiResponse.ok("Template updated successfully", dto));
    }

    @DeleteMapping("/{groupId}")
    @PreAuthorize("hasAuthority('" + Permissions.SCHEDULE_TEMPLATE_WRITE + "')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long groupId) {
        scheduleTemplateService.deactivate(groupId);
        return ResponseEntity.ok(ApiResponse.ok("Template deleted successfully", null));
    }

    @PatchMapping("/{groupId}/reactivate")
    @PreAuthorize("hasAuthority('" + Permissions.SCHEDULE_TEMPLATE_WRITE + "')")
    public ResponseEntity<ApiResponse<Void>> reactivate(@PathVariable Long groupId) {
        scheduleTemplateService.reactivate(groupId);
        return ResponseEntity.ok(ApiResponse.ok("template.reactivated", null));
    }

    @PostMapping("/filter")
    @PreAuthorize("hasAuthority('" + Permissions.SCHEDULE_TEMPLATE_READ + "')")
    public ResponseEntity<ApiResponse<Page<ScheduleTemplateDTO>>> filter(@RequestBody @Valid FilterRequest request) {
        Page<ScheduleTemplateDTO> page = scheduleTemplateService.filter(request).map(scheduleTemplateMapper::toDTO);
        return ResponseEntity.ok(ApiResponse.ok(page));
    }
}
