package com.example.tgs_dev.controller;

import com.example.tgs_dev.controller.request.RotationEntryRequest;
import com.example.tgs_dev.controller.request.RotationRequest;
import com.example.tgs_dev.controller.response.ApiResponse;
import com.example.tgs_dev.controller.response.RotationDTO;
import com.example.tgs_dev.controller.response.VehicleRotationDTO;
import com.example.tgs_dev.entity.RotationEntry;
import com.example.tgs_dev.entity.VehicleRotation;
import com.example.tgs_dev.mapper.RotationMapper;
import com.example.tgs_dev.repository.filter.FilterRequest;
import com.example.tgs_dev.service.RotationEntryService;
import com.example.tgs_dev.service.ScheduleTemplateService;
import com.example.tgs_dev.service.VehicleRotationService;
import com.example.tgs_dev.service.VehicleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rotation")
@RequiredArgsConstructor
public class VehicleRotationController {

    private final VehicleRotationService vehicleRotationService;
    private final RotationEntryService rotationEntryService;
    private final VehicleService vehicleService;
    private final ScheduleTemplateService scheduleTemplateService;
    private final RotationMapper rotationMapper;

    @GetMapping
    public ResponseEntity<ApiResponse<List<VehicleRotationDTO>>> findAllRotations() {
        List<VehicleRotationDTO> rotations = rotationMapper.toRotationDTOList(vehicleRotationService.findAll());
        return ResponseEntity.ok(ApiResponse.ok(rotations));
    }

    @GetMapping("/entries/{id}")
    public ResponseEntity<ApiResponse<RotationDTO>> findEntriesByRotation(@PathVariable Integer id) {
        VehicleRotation rotation = vehicleRotationService.findById(id);
        List<RotationEntry> entries = rotationEntryService.findByRotation(rotation);
        return ResponseEntity.ok(ApiResponse.ok(rotationMapper.toDTO(rotation, entries)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RotationDTO>> createRotation(@RequestBody @Valid RotationRequest request) {
        VehicleRotation rotation = vehicleRotationService.save(rotationMapper.toEntity(request));
        List<RotationEntry> entries = buildEntries(request.entries(), rotation);
        List<RotationEntry> savedEntries = rotationEntryService.saveAll(rotation, entries);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Rotation created successfully", rotationMapper.toDTO(rotation, savedEntries)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<RotationDTO>> updateRotation(@PathVariable Integer id,
                                                                    @RequestBody @Valid RotationRequest request) {
        VehicleRotation rotation = vehicleRotationService.findById(id);
        rotationEntryService.deleteAll(rotationEntryService.findByRotation(rotation));
        rotationMapper.updateEntity(rotation, request);
        VehicleRotation updated = vehicleRotationService.save(rotation);
        List<RotationEntry> entries = buildEntries(request.entries(), updated);
        List<RotationEntry> savedEntries = rotationEntryService.saveAll(updated, entries);
        return ResponseEntity.ok(ApiResponse.ok("Rotation updated successfully", rotationMapper.toDTO(updated, savedEntries)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Integer id) {
        vehicleRotationService.delete(vehicleRotationService.findById(id));
        return ResponseEntity.ok(ApiResponse.ok("Rotation deleted successfully", null));
    }

    @PostMapping("/filter")
    public ResponseEntity<ApiResponse<Page<VehicleRotationDTO>>> filter(@RequestBody @Valid FilterRequest request) {
        Page<VehicleRotationDTO> page = vehicleRotationService.filter(request).map(rotationMapper::toRotationDTO);
        return ResponseEntity.ok(ApiResponse.ok(page));
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private List<RotationEntry> buildEntries(List<RotationEntryRequest> requests, VehicleRotation rotation) {
        return requests.stream().map(r -> {
            RotationEntry entry = new RotationEntry();
            entry.setVehicle(vehicleService.findById(r.vehicleId()));
            entry.setScheduleTemplate(scheduleTemplateService.findById(r.scheduleTemplateId()));
            entry.setListPosition(r.listPosition());
            entry.setVehicleRotation(rotation);
            return entry;
        }).toList();
    }
}
