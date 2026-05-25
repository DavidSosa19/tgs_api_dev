package com.example.tgs_dev.controller;

import com.example.tgs_dev.controller.request.ScheduleTemplateVersionRequest;
import com.example.tgs_dev.controller.response.ApiResponse;
import com.example.tgs_dev.controller.response.ScheduleTemplateVersionDTO;
import com.example.tgs_dev.security.Permissions;
import com.example.tgs_dev.service.ScheduleTemplateVersionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST endpoints for managing {@link com.example.tgs_dev.entity.ScheduleTemplateVersion}
 * records nested under a parent schedule template.
 *
 * <p>Base path: {@code /api/schedule-templates/{templateId}/versions}
 *
 * <p>Reads require {@link Permissions#SCHEDULE_TEMPLATE_READ}; writes require
 * {@link Permissions#SCHEDULE_TEMPLATE_WRITE}.
 */
@RestController
@RequestMapping("/api/schedule-templates/{templateId}/versions")
public class ScheduleTemplateVersionController {

    private final ScheduleTemplateVersionService service;

    public ScheduleTemplateVersionController(ScheduleTemplateVersionService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + Permissions.SCHEDULE_TEMPLATE_READ + "')")
    public ResponseEntity<ApiResponse<List<ScheduleTemplateVersionDTO>>> findAll(
            @PathVariable Integer templateId) {

        List<ScheduleTemplateVersionDTO> body = service.findAllByTemplate(templateId)
                .stream()
                .map(ScheduleTemplateVersionDTO::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(body));
    }

    @GetMapping("/{versionId}")
    @PreAuthorize("hasAuthority('" + Permissions.SCHEDULE_TEMPLATE_READ + "')")
    public ResponseEntity<ApiResponse<ScheduleTemplateVersionDTO>> findById(
            @PathVariable Integer templateId,
            @PathVariable Integer versionId) {

        ScheduleTemplateVersionDTO dto =
                ScheduleTemplateVersionDTO.from(service.findById(templateId, versionId));
        return ResponseEntity.ok(ApiResponse.ok(dto));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + Permissions.SCHEDULE_TEMPLATE_WRITE + "')")
    public ResponseEntity<ApiResponse<ScheduleTemplateVersionDTO>> create(
            @PathVariable Integer templateId,
            @Valid @RequestBody ScheduleTemplateVersionRequest request) {

        ScheduleTemplateVersionDTO dto =
                ScheduleTemplateVersionDTO.from(service.create(templateId, request));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(dto));
    }

    @PutMapping("/{versionId}")
    @PreAuthorize("hasAuthority('" + Permissions.SCHEDULE_TEMPLATE_WRITE + "')")
    public ResponseEntity<ApiResponse<ScheduleTemplateVersionDTO>> update(
            @PathVariable Integer templateId,
            @PathVariable Integer versionId,
            @Valid @RequestBody ScheduleTemplateVersionRequest request) {

        ScheduleTemplateVersionDTO dto =
                ScheduleTemplateVersionDTO.from(service.update(templateId, versionId, request));
        return ResponseEntity.ok(ApiResponse.ok(dto));
    }

    @DeleteMapping("/{versionId}")
    @PreAuthorize("hasAuthority('" + Permissions.SCHEDULE_TEMPLATE_WRITE + "')")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Integer templateId,
            @PathVariable Integer versionId) {

        service.delete(templateId, versionId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
