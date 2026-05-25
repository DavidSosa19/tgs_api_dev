package com.example.tgs_dev.controller;

import com.example.tgs_dev.controller.request.RouteCalendarOverrideRequest;
import com.example.tgs_dev.controller.response.ApiResponse;
import com.example.tgs_dev.controller.response.RouteCalendarOverrideDTO;
import com.example.tgs_dev.controller.response.RouteTimeRangeDTO;
import com.example.tgs_dev.entity.RouteCalendarOverride;
import com.example.tgs_dev.entity.RouteCalendarOverrideRange;
import com.example.tgs_dev.security.Permissions;
import com.example.tgs_dev.service.RouteCalendarOverrideService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Sub-resource controller: calendar overrides are always addressed relative to a route.
 *
 * <p>Base path: {@code /api/route/{routeId}/overrides}
 */
@RestController
@RequestMapping("/api/route/{routeId}/overrides")
@RequiredArgsConstructor
public class RouteCalendarOverrideController {

    private final RouteCalendarOverrideService overrideService;

    @GetMapping
    @PreAuthorize("hasAuthority('" + Permissions.ROUTE_READ + "')")
    public ResponseEntity<ApiResponse<List<RouteCalendarOverrideDTO>>> getAll(
            @PathVariable Integer routeId) {
        List<RouteCalendarOverrideDTO> list = overrideService.findAllByRoute(routeId)
                .stream().map(this::toDTO).toList();
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.ROUTE_READ + "')")
    public ResponseEntity<ApiResponse<RouteCalendarOverrideDTO>> getById(
            @PathVariable Integer routeId,
            @PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.ok(toDTO(overrideService.findById(id))));
    }

    /**
     * Creates or replaces the override for {@code routeId} on the date specified in the request.
     * Idempotent: calling twice with the same date replaces the first override.
     */
    @PostMapping
    @PreAuthorize("hasAuthority('" + Permissions.ROUTE_WRITE + "')")
    public ResponseEntity<ApiResponse<RouteCalendarOverrideDTO>> save(
            @PathVariable Integer routeId,
            @RequestBody @Valid RouteCalendarOverrideRequest request) {
        RouteCalendarOverride saved = overrideService.save(routeId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Calendar override saved", toDTO(saved)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.ROUTE_WRITE + "')")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Integer routeId,
            @PathVariable Integer id) {
        overrideService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Calendar override deleted", null));
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private RouteCalendarOverrideDTO toDTO(RouteCalendarOverride ov) {
        List<RouteTimeRangeDTO> rangeDTOs = ov.getRanges().stream()
                .map(this::toRangeDTO).toList();
        return new RouteCalendarOverrideDTO(
                ov.getId(),
                ov.getOverrideDate(),
                ov.getUseTimeRanges(),
                ov.getBaseDuration(),
                rangeDTOs);
    }

    private RouteTimeRangeDTO toRangeDTO(RouteCalendarOverrideRange r) {
        return new RouteTimeRangeDTO(
                r.getId(),
                r.getRangeStart(),
                r.getRangeEnd(),
                r.getDurationMinutes(),
                r.getSortOrder(),
                r.isCrossesMidnight());
    }
}
