package com.example.tgs_dev.controller;

import com.example.tgs_dev.controller.request.VehicleRequest;
import com.example.tgs_dev.controller.response.ApiResponse;
import com.example.tgs_dev.controller.response.VehicleDTO;
import com.example.tgs_dev.entity.Person;
import com.example.tgs_dev.entity.Vehicle;
import com.example.tgs_dev.mapper.VehicleMapper;
import com.example.tgs_dev.repository.filter.FilterRequest;
import com.example.tgs_dev.service.PersonService;
import com.example.tgs_dev.service.VehicleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/vehicle")
@RequiredArgsConstructor
public class VehicleController {

    private final VehicleService vehicleService;
    private final PersonService personService;
    private final VehicleMapper vehicleMapper;

    @GetMapping
    public ResponseEntity<ApiResponse<List<VehicleDTO>>> getAll() {
        List<VehicleDTO> vehicles = vehicleMapper.toDTOList(vehicleService.findAll());
        return ResponseEntity.ok(ApiResponse.ok(vehicles));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<VehicleDTO>> getById(@PathVariable Integer id) {
        VehicleDTO dto = vehicleMapper.toDTO(vehicleService.findById(id));
        return ResponseEntity.ok(ApiResponse.ok(dto));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<VehicleDTO>> create(@RequestBody @Valid VehicleRequest request) {
        Vehicle vehicle = buildVehicle(request, new Vehicle());
        VehicleDTO dto = vehicleMapper.toDTO(vehicleService.save(vehicle));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Vehicle created successfully", dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<VehicleDTO>> update(@PathVariable Integer id,
                                                          @RequestBody @Valid VehicleRequest request) {
        Vehicle vehicle = buildVehicle(request, vehicleService.findById(id));
        VehicleDTO dto = vehicleMapper.toDTO(vehicleService.save(vehicle));
        return ResponseEntity.ok(ApiResponse.ok("Vehicle updated successfully", dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Integer id) {
        vehicleService.delete(vehicleService.findById(id));
        return ResponseEntity.ok(ApiResponse.ok("Vehicle deleted successfully", null));
    }

    @PostMapping("/filter")
    public ResponseEntity<ApiResponse<Page<VehicleDTO>>> filter(@RequestBody @Valid FilterRequest request) {
        Page<VehicleDTO> page = vehicleService.filter(request).map(vehicleMapper::toDTO);
        return ResponseEntity.ok(ApiResponse.ok(page));
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private Vehicle buildVehicle(VehicleRequest request, Vehicle vehicle) {
        vehicle.setVehicleNumber(request.vehicleNumber());
        vehicle.setLicensePlate(request.licensePlate());
        Person owner = request.ownerId() != null
                ? personService.findById(request.ownerId())
                : null;
        vehicle.setOwner(owner);
        return vehicle;
    }
}
