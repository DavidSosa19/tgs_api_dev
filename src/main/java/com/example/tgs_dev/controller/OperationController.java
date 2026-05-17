package com.example.tgs_dev.controller;

import com.example.tgs_dev.controller.response.ApiResponse;
import com.example.tgs_dev.controller.request.InitOperationRequest;
import com.example.tgs_dev.controller.request.RemoveVehicleRequest;
import com.example.tgs_dev.entity.Route;
import com.example.tgs_dev.entity.RouteOperation;
import com.example.tgs_dev.security.Permissions;
import com.example.tgs_dev.service.OperationOrchestratorService;
import com.example.tgs_dev.service.RouteOperationService;
import com.example.tgs_dev.service.RouteService;
import com.example.tgs_dev.service.VehicleRemovalService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/routeOperation")
public class OperationController {

    private final OperationOrchestratorService orchestratorService;
    private final RouteOperationService routeOperationService;
    private final VehicleRemovalService vehicleRemovalService;
    private final RouteService routeService;

    public OperationController(OperationOrchestratorService orchestratorService,
                               RouteOperationService routeOperationService,
                               VehicleRemovalService vehicleRemovalService,
                               RouteService routeService) {
        this.orchestratorService = orchestratorService;
        this.routeOperationService = routeOperationService;
        this.vehicleRemovalService = vehicleRemovalService;
        this.routeService = routeService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + Permissions.OPERATION_MANAGE + "')")
    public ResponseEntity<ApiResponse<Void>> create(@RequestBody @Valid InitOperationRequest operationRequest){
        Route route = routeService.findById(operationRequest.routeId());
        orchestratorService.initOperation(route, operationRequest.date());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("routes.initialized.success", null));
    }

    @PostMapping("/all")
    @PreAuthorize("hasAuthority('" + Permissions.OPERATION_MANAGE + "')")
    public ResponseEntity<ApiResponse<Void>> createAll(@RequestBody @Valid InitOperationRequest operationRequest){
        orchestratorService.initAllOperations(operationRequest.date());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("routes.initialized.success", null));
    }

    @GetMapping("/{date}")
    @PreAuthorize("hasAuthority('" + Permissions.OPERATION_READ + "')")
    public ResponseEntity<ApiResponse<List<RouteOperation>>> findAllByDate(@PathVariable LocalDate date){
        return ResponseEntity.ok(ApiResponse.ok(routeOperationService.findAllByDate(date)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.OPERATION_MANAGE + "')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Integer id) {
        routeOperationService.softDelete(routeOperationService.findById(id));
        return ResponseEntity.ok(ApiResponse.ok("operation.deleted.success", null));
    }

    @DeleteMapping("/all/{date}")
    @PreAuthorize("hasAuthority('" + Permissions.OPERATION_MANAGE + "')")
    public ResponseEntity<ApiResponse<Void>> deleteAll(@PathVariable LocalDate date) {
        routeOperationService.softDeleteAllByDate(date);
        return ResponseEntity.ok(ApiResponse.ok("operations.all.deleted.success", null));
    }

    @PostMapping("/vehicle/remove")
    @PreAuthorize("hasAuthority('" + Permissions.OPERATION_MANAGE + "')")
    public ResponseEntity<ApiResponse<Void>> removeVehicle(@RequestBody @Valid RemoveVehicleRequest request) {
        vehicleRemovalService.handleRemoval(request);
        return ResponseEntity.ok(ApiResponse.ok("vehicle.removed.success", null));
    }
}
