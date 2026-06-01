package com.example.tgs_dev.controller;

import com.example.tgs_dev.controller.request.VehicleRequest;
import com.example.tgs_dev.controller.response.ApiResponse;
import com.example.tgs_dev.controller.response.VehicleDTO;
import com.example.tgs_dev.mapper.VehicleMapper;
import com.example.tgs_dev.repository.filter.FilterRequest;
import com.example.tgs_dev.security.Permissions;
import com.example.tgs_dev.service.VehicleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST endpoints for {@link com.example.tgs_dev.entity.Vehicle}.
 *
 * <p>Path-variable {@code groupId} is the SCD stable business identity
 * ({@code vehicle_group.id}), not the surrogate version id.
 */
@RestController
@RequestMapping("/api/vehicle")
@RequiredArgsConstructor
public class VehicleController {

    private final VehicleService vehicleService;
    private final VehicleMapper  vehicleMapper;

    @GetMapping
    @PreAuthorize("hasAuthority('" + Permissions.VEHICLE_READ + "')")
    public ResponseEntity<ApiResponse<List<VehicleDTO>>> getAll() {
        List<VehicleDTO> vehicles = vehicleMapper.toDTOList(vehicleService.findAll());
        return ResponseEntity.ok(ApiResponse.ok(vehicles));
    }

    @GetMapping("/{groupId}")
    @PreAuthorize("hasAuthority('" + Permissions.VEHICLE_READ + "')")
    public ResponseEntity<ApiResponse<VehicleDTO>> getById(@PathVariable Long groupId) {
        VehicleDTO dto = vehicleMapper.toDTO(vehicleService.findByGroupId(groupId));
        return ResponseEntity.ok(ApiResponse.ok(dto));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + Permissions.VEHICLE_WRITE + "')")
    public ResponseEntity<ApiResponse<VehicleDTO>> create(@RequestBody @Valid VehicleRequest request) {
        VehicleDTO dto = vehicleMapper.toDTO(vehicleService.create(request));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Vehicle created successfully", dto));
    }

    @PutMapping("/{groupId}")
    @PreAuthorize("hasAuthority('" + Permissions.VEHICLE_WRITE + "')")
    public ResponseEntity<ApiResponse<VehicleDTO>> update(@PathVariable Long groupId,
                                                          @RequestBody @Valid VehicleRequest request) {
        VehicleDTO dto = vehicleMapper.toDTO(vehicleService.update(groupId, request));
        return ResponseEntity.ok(ApiResponse.ok("Vehicle updated successfully", dto));
    }

    @DeleteMapping("/{groupId}")
    @PreAuthorize("hasAuthority('" + Permissions.VEHICLE_WRITE + "')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long groupId) {
        vehicleService.deactivate(groupId);
        return ResponseEntity.ok(ApiResponse.ok("Vehicle deleted successfully", null));
    }

    @PatchMapping("/{groupId}/reactivate")
    @PreAuthorize("hasAuthority('" + Permissions.VEHICLE_WRITE + "')")
    public ResponseEntity<ApiResponse<Void>> reactivate(@PathVariable Long groupId) {
        vehicleService.reactivate(groupId);
        return ResponseEntity.ok(ApiResponse.ok("vehicle.reactivated", null));
    }

    @PostMapping("/filter")
    @PreAuthorize("hasAuthority('" + Permissions.VEHICLE_READ + "')")
    public ResponseEntity<ApiResponse<Page<VehicleDTO>>> filter(@RequestBody @Valid FilterRequest request) {
        Page<VehicleDTO> page = vehicleService.filter(request).map(vehicleMapper::toDTO);
        return ResponseEntity.ok(ApiResponse.ok(page));
    }
}
