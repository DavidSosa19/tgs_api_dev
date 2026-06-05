package com.example.tgs_dev.controller;

import com.example.tgs_dev.controller.request.SeasonalPatternRequest;
import com.example.tgs_dev.controller.response.ApiResponse;
import com.example.tgs_dev.controller.response.RouteTimeRangeDTO;
import com.example.tgs_dev.controller.response.SeasonalPatternDTO;
import com.example.tgs_dev.entity.SeasonalPattern;
import com.example.tgs_dev.entity.SeasonalPatternRange;
import com.example.tgs_dev.security.Permissions;
import com.example.tgs_dev.service.SeasonalPatternService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Sub-resource controller for seasonal patterns.
 *
 * <p>Base path: {@code /api/route/{routeId}/seasonal-patterns}
 */
@RestController
@RequestMapping("/api/routes/{routeId}/seasonal-patterns")
@RequiredArgsConstructor
public class SeasonalPatternController {

    private final SeasonalPatternService patternService;

    @GetMapping
    @PreAuthorize("hasAuthority('" + Permissions.ROUTE_READ + "')")
    public ResponseEntity<ApiResponse<List<SeasonalPatternDTO>>> getAll(
            @PathVariable Integer routeId) {
        List<SeasonalPatternDTO> list = patternService.findAllByRoute(routeId)
                .stream().map(this::toDTO).toList();
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.ROUTE_READ + "')")
    public ResponseEntity<ApiResponse<SeasonalPatternDTO>> getById(
            @PathVariable Integer routeId,
            @PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.ok(toDTO(patternService.findById(id))));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + Permissions.ROUTE_WRITE + "')")
    public ResponseEntity<ApiResponse<SeasonalPatternDTO>> create(
            @PathVariable Integer routeId,
            @RequestBody @Valid SeasonalPatternRequest request) {
        SeasonalPattern saved = patternService.save(routeId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Seasonal pattern created", toDTO(saved)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.ROUTE_WRITE + "')")
    public ResponseEntity<ApiResponse<SeasonalPatternDTO>> update(
            @PathVariable Integer routeId,
            @PathVariable Integer id,
            @RequestBody @Valid SeasonalPatternRequest request) {
        SeasonalPattern updated = patternService.update(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Seasonal pattern updated", toDTO(updated)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.ROUTE_WRITE + "')")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Integer routeId,
            @PathVariable Integer id) {
        patternService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Seasonal pattern deleted", null));
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private SeasonalPatternDTO toDTO(SeasonalPattern sp) {
        List<RouteTimeRangeDTO> rangeDTOs = sp.getRanges().stream()
                .map(this::toRangeDTO).toList();
        return new SeasonalPatternDTO(
                sp.getId(),
                sp.getName(),
                sp.getSeasonFrom(),
                sp.getSeasonTo(),
                sp.getUseTimeRanges(),
                sp.getBaseDuration(),
                rangeDTOs);
    }

    private RouteTimeRangeDTO toRangeDTO(SeasonalPatternRange r) {
        return new RouteTimeRangeDTO(
                r.getId(),
                r.getRangeStart(),
                r.getRangeEnd(),
                r.getDurationMinutes(),
                0,   // seasonal patterns do not carry headway config
                r.getSortOrder(),
                r.isCrossesMidnight());
    }
}
