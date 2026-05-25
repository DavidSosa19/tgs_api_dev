package com.example.tgs_dev.controller;

import com.example.tgs_dev.controller.request.RouteOperationalPeriodRequest;
import com.example.tgs_dev.controller.response.ApiResponse;
import com.example.tgs_dev.controller.response.RouteOperationalPeriodDTO;
import com.example.tgs_dev.security.Permissions;
import com.example.tgs_dev.service.RouteOperationalPeriodService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST endpoints for managing {@link com.example.tgs_dev.entity.RouteOperationalPeriod}
 * records nested under a parent route.
 *
 * <p>Base path: {@code /api/routes/{routeId}/operational-periods}
 *
 * <p>Reads require {@link Permissions#ROUTE_READ}; writes require
 * {@link Permissions#ROUTE_WRITE} — no new permissions needed.
 */
@RestController
@RequestMapping("/api/routes/{routeId}/operational-periods")
public class RouteOperationalPeriodController {

    private final RouteOperationalPeriodService service;

    public RouteOperationalPeriodController(RouteOperationalPeriodService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + Permissions.ROUTE_READ + "')")
    public ResponseEntity<ApiResponse<List<RouteOperationalPeriodDTO>>> findAll(
            @PathVariable Integer routeId) {

        List<RouteOperationalPeriodDTO> body = service.findAllByRoute(routeId)
                .stream()
                .map(RouteOperationalPeriodDTO::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(body));
    }

    @GetMapping("/{periodId}")
    @PreAuthorize("hasAuthority('" + Permissions.ROUTE_READ + "')")
    public ResponseEntity<ApiResponse<RouteOperationalPeriodDTO>> findById(
            @PathVariable Integer routeId,
            @PathVariable Integer periodId) {

        RouteOperationalPeriodDTO dto =
                RouteOperationalPeriodDTO.from(service.findById(routeId, periodId));
        return ResponseEntity.ok(ApiResponse.ok(dto));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + Permissions.ROUTE_WRITE + "')")
    public ResponseEntity<ApiResponse<RouteOperationalPeriodDTO>> create(
            @PathVariable Integer routeId,
            @Valid @RequestBody RouteOperationalPeriodRequest request) {

        RouteOperationalPeriodDTO dto =
                RouteOperationalPeriodDTO.from(service.create(routeId, request));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(dto));
    }

    @PutMapping("/{periodId}")
    @PreAuthorize("hasAuthority('" + Permissions.ROUTE_WRITE + "')")
    public ResponseEntity<ApiResponse<RouteOperationalPeriodDTO>> update(
            @PathVariable Integer routeId,
            @PathVariable Integer periodId,
            @Valid @RequestBody RouteOperationalPeriodRequest request) {

        RouteOperationalPeriodDTO dto =
                RouteOperationalPeriodDTO.from(service.update(routeId, periodId, request));
        return ResponseEntity.ok(ApiResponse.ok(dto));
    }

    @DeleteMapping("/{periodId}")
    @PreAuthorize("hasAuthority('" + Permissions.ROUTE_WRITE + "')")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Integer routeId,
            @PathVariable Integer periodId) {

        service.delete(routeId, periodId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
