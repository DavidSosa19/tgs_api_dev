package com.example.tgs_dev.controller;

import com.example.tgs_dev.controller.request.ScheduleTemplateRequest;
import com.example.tgs_dev.controller.response.ApiResponse;
import com.example.tgs_dev.controller.response.ScheduleTemplateDTO;
import com.example.tgs_dev.entity.Route;
import com.example.tgs_dev.entity.ScheduleTemplate;
import com.example.tgs_dev.mapper.ScheduleTemplateMapper;
import com.example.tgs_dev.repository.filter.FilterRequest;
import com.example.tgs_dev.service.RouteService;
import com.example.tgs_dev.service.ScheduleTemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/scheduleTemplate")
@RequiredArgsConstructor
public class ScheduleTemplateController {

    private final ScheduleTemplateService scheduleTemplateService;
    private final RouteService routeService;
    private final ScheduleTemplateMapper scheduleTemplateMapper;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ScheduleTemplateDTO>>> getAll() {
        List<ScheduleTemplateDTO> templates = scheduleTemplateMapper.toDTOList(scheduleTemplateService.findAll());
        return ResponseEntity.ok(ApiResponse.ok(templates));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ScheduleTemplateDTO>> getById(@PathVariable Integer id) {
        ScheduleTemplateDTO dto = scheduleTemplateMapper.toDTO(scheduleTemplateService.findById(id));
        return ResponseEntity.ok(ApiResponse.ok(dto));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ScheduleTemplateDTO>> create(@RequestBody @Valid ScheduleTemplateRequest request) {
        ScheduleTemplate template = buildTemplate(request, new ScheduleTemplate());
        ScheduleTemplateDTO dto = scheduleTemplateMapper.toDTO(scheduleTemplateService.save(template));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Template created successfully", dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ScheduleTemplateDTO>> update(@PathVariable Integer id,
                                                                    @RequestBody @Valid ScheduleTemplateRequest request) {
        ScheduleTemplate template = scheduleTemplateService.findById(id);
        Route route = routeService.findById(request.routeId());
        Route secondaryRoute = request.secondaryRouteId() != null
                ? routeService.findById(request.secondaryRouteId())
                : null;
        scheduleTemplateMapper.updateEntity(template, request, route, secondaryRoute);
        ScheduleTemplateDTO dto = scheduleTemplateMapper.toDTO(scheduleTemplateService.save(template));
        return ResponseEntity.ok(ApiResponse.ok("Template updated successfully", dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Integer id) {
        scheduleTemplateService.delete(scheduleTemplateService.findById(id));
        return ResponseEntity.ok(ApiResponse.ok("Template deleted successfully", null));
    }

    @PostMapping("/filter")
    public ResponseEntity<ApiResponse<Page<ScheduleTemplateDTO>>> filter(@RequestBody @Valid FilterRequest request) {
        Page<ScheduleTemplateDTO> page = scheduleTemplateService.filter(request).map(scheduleTemplateMapper::toDTO);
        return ResponseEntity.ok(ApiResponse.ok(page));
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private ScheduleTemplate buildTemplate(ScheduleTemplateRequest request, ScheduleTemplate template) {
        Route route = routeService.findById(request.routeId());
        Route secondaryRoute = request.secondaryRouteId() != null
                ? routeService.findById(request.secondaryRouteId())
                : null;
        template.setRoute(route);
        template.setSecondaryRoute(secondaryRoute);
        template.setTemplateNumber(request.templateNumber());
        template.setName(request.name());
        template.setStartTime(request.startTime());
        if (request.active() != null) {
            template.setActive(request.active());
        }
        return template;
    }
}
