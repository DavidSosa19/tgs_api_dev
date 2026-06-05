package com.example.tgs_dev.controller;

import com.example.tgs_dev.controller.response.ApiResponse;
import com.example.tgs_dev.controller.request.InitOperationRequest;
import com.example.tgs_dev.controller.request.RemoveVehicleRequest;
import com.example.tgs_dev.controller.response.InitOperationsResponse;
import com.example.tgs_dev.controller.response.RouteOperationDTO;
import com.example.tgs_dev.entity.Route;
import com.example.tgs_dev.security.Permissions;
import com.example.tgs_dev.service.InitOperationsResult;
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
@RequestMapping("/api/route-operations")
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
        Route route = routeService.findByGroupId(operationRequest.routeGroupId());
        orchestratorService.initOperation(route, operationRequest.date());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("routes.initialized.success", null));
    }

    /**
     * Initialises operations for every active route on the requested date.
     *
     * <p>The response carries a per-route breakdown ({@code initialized},
     * {@code skipped}, {@code failed} + {@code failures[]}) so the frontend can
     * present a precise summary instead of a single misleading count.
     *
     * <h3>Status code semantics</h3>
     * <ul>
     *   <li><b>201 Created</b> — at least one route was initialised
     *       (full success or partial success).  The message key disambiguates:
     *       {@code routes.initialized.success} or {@code routes.initialized.partial}.</li>
     *   <li><b>200 OK</b> — no-op: no active routes or all already initialised.
     *       Message: {@code routes.initialized.none}.</li>
     *   <li><b>422 Unprocessable Entity</b> — every pending route failed.
     *       Message: {@code routes.initialized.allFailed}.  The failures array
     *       carries the reasons for each route.</li>
     * </ul>
     */
    @PostMapping("/all")
    @PreAuthorize("hasAuthority('" + Permissions.OPERATION_MANAGE + "')")
    public ResponseEntity<ApiResponse<InitOperationsResponse>> createAll(
            @RequestBody @Valid InitOperationRequest operationRequest) {

        InitOperationsResult   result = orchestratorService.initAllOperations(operationRequest.date());
        InitOperationsResponse body   = InitOperationsResponse.from(result);

        if (result.isNoop()) {
            return ResponseEntity.ok(ApiResponse.ok("routes.initialized.none", body));
        }
        if (result.isAllFailed()) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                    .body(ApiResponse.ok("routes.initialized.allFailed", body));
        }
        String messageKey = result.isPartial()
                ? "routes.initialized.partial"
                : "routes.initialized.success";
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(messageKey, body));
    }

    @GetMapping("/{date}")
    @PreAuthorize("hasAuthority('" + Permissions.OPERATION_READ + "')")
    public ResponseEntity<ApiResponse<List<RouteOperationDTO>>> findAllByDate(@PathVariable LocalDate date){
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
