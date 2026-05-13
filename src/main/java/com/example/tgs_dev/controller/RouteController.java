package com.example.tgs_dev.controller;

import com.example.tgs_dev.controller.request.RouteRequest;
import com.example.tgs_dev.controller.response.ApiResponse;
import com.example.tgs_dev.controller.response.RouteDTO;
import com.example.tgs_dev.entity.Route;
import com.example.tgs_dev.mapper.RouteMapper;
import com.example.tgs_dev.repository.filter.FilterRequest;
import com.example.tgs_dev.service.RouteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/route")
@RequiredArgsConstructor
public class RouteController {

    private final RouteService routeService;
    private final RouteMapper routeMapper;

    @GetMapping
    public ResponseEntity<ApiResponse<List<RouteDTO>>> getAll() {
        List<RouteDTO> routes = routeMapper.toDTOList(routeService.findAll());
        return ResponseEntity.ok(ApiResponse.ok(routes));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RouteDTO>> getById(@PathVariable Integer id) {
        RouteDTO dto = routeMapper.toDTO(routeService.findById(id));
        return ResponseEntity.ok(ApiResponse.ok(dto));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RouteDTO>> create(@RequestBody @Valid RouteRequest request) {
        Route saved = routeService.save(routeMapper.toEntity(request));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Route created successfully", routeMapper.toDTO(saved)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<RouteDTO>> update(@PathVariable Integer id,
                                                        @RequestBody @Valid RouteRequest request) {
        Route route = routeService.findById(id);
        routeMapper.updateEntity(route, request);
        RouteDTO dto = routeMapper.toDTO(routeService.save(route));
        return ResponseEntity.ok(ApiResponse.ok("Route updated successfully", dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Integer id) {
        routeService.delete(routeService.findById(id));
        return ResponseEntity.ok(ApiResponse.ok("Route deleted successfully", null));
    }

    @PostMapping("/filter")
    public ResponseEntity<ApiResponse<Page<RouteDTO>>> filter(@RequestBody @Valid FilterRequest request) {
        Page<RouteDTO> page = routeService.filter(request).map(routeMapper::toDTO);
        return ResponseEntity.ok(ApiResponse.ok(page));
    }
}
