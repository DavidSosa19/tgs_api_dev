package com.example.tgs_dev.controller;

import com.example.tgs_dev.controller.request.RouteRequest;
import com.example.tgs_dev.controller.response.ApiResponse;
import com.example.tgs_dev.controller.response.RouteDTO;
import com.example.tgs_dev.mapper.RouteMapper;
import com.example.tgs_dev.repository.filter.FilterRequest;
import com.example.tgs_dev.security.Permissions;
import com.example.tgs_dev.service.RouteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST endpoints for {@link com.example.tgs_dev.entity.Route}.
 *
 * <p>Path-variable {@code groupId} is the SCD stable business identity
 * ({@code route_group.id}), not the surrogate version id.
 */
@RestController
@RequestMapping("/api/route")
@RequiredArgsConstructor
public class RouteController {

    private final RouteService routeService;
    private final RouteMapper  routeMapper;

    @GetMapping
    @PreAuthorize("hasAuthority('" + Permissions.ROUTE_READ + "')")
    public ResponseEntity<ApiResponse<List<RouteDTO>>> getAll() {
        // Listing includes inactive routes so the UI can offer reactivation.
        List<RouteDTO> routes = routeMapper.toDTOList(routeService.findAllCurrent());
        return ResponseEntity.ok(ApiResponse.ok(routes));
    }

    @GetMapping("/{groupId}")
    @PreAuthorize("hasAuthority('" + Permissions.ROUTE_READ + "')")
    public ResponseEntity<ApiResponse<RouteDTO>> getById(@PathVariable Long groupId) {
        RouteDTO dto = routeMapper.toDTO(routeService.findByGroupId(groupId));
        return ResponseEntity.ok(ApiResponse.ok(dto));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + Permissions.ROUTE_WRITE + "')")
    public ResponseEntity<ApiResponse<RouteDTO>> create(@RequestBody @Valid RouteRequest request) {
        RouteDTO dto = routeMapper.toDTO(routeService.create(request));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Route created successfully", dto));
    }

    @PutMapping("/{groupId}")
    @PreAuthorize("hasAuthority('" + Permissions.ROUTE_WRITE + "')")
    public ResponseEntity<ApiResponse<RouteDTO>> update(@PathVariable Long groupId,
                                                        @RequestBody @Valid RouteRequest request) {
        RouteDTO dto = routeMapper.toDTO(routeService.update(groupId, request));
        return ResponseEntity.ok(ApiResponse.ok("Route updated successfully", dto));
    }

    @DeleteMapping("/{groupId}")
    @PreAuthorize("hasAuthority('" + Permissions.ROUTE_WRITE + "')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long groupId) {
        routeService.deactivate(groupId);
        return ResponseEntity.ok(ApiResponse.ok("Route deleted successfully", null));
    }

    @PostMapping("/filter")
    @PreAuthorize("hasAuthority('" + Permissions.ROUTE_READ + "')")
    public ResponseEntity<ApiResponse<Page<RouteDTO>>> filter(@RequestBody @Valid FilterRequest request) {
        Page<RouteDTO> page = routeService.filter(request).map(routeMapper::toDTO);
        return ResponseEntity.ok(ApiResponse.ok(page));
    }
}
