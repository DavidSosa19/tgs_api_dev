package com.example.tgs_dev.controller;

import com.example.tgs_dev.controller.response.ApiResponse;
import com.example.tgs_dev.controller.response.AssignmentSchedulesDTO;
import com.example.tgs_dev.service.MatrixService;
import com.example.tgs_dev.service.RouteOperationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/viewer")
public class MatrixViewerController {

    private final MatrixService matrixService;
    private final RouteOperationService routeOperationService;

    public MatrixViewerController(MatrixService matrixService, RouteOperationService routeOperationService) {
        this.matrixService = matrixService;
        this.routeOperationService = routeOperationService;
    }

    @GetMapping("/route-operations/{id}/assignments")
    public ResponseEntity<ApiResponse<List<AssignmentSchedulesDTO>>> getAssignmentsByRouteOperation(@PathVariable Integer id){
        return ResponseEntity.ok(ApiResponse.ok(matrixService.getAssignmentSchedules(id))) ;
    }
}
