package com.example.tgs_dev.controller;

import com.example.tgs_dev.controller.response.ApiResponse;
import com.example.tgs_dev.controller.response.AssignmentSchedulesDTO;
import com.example.tgs_dev.security.Permissions;
import com.example.tgs_dev.service.MatrixService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/viewer")
public class MatrixViewerController {

    private final MatrixService matrixService;

    public MatrixViewerController(MatrixService matrixService) {
        this.matrixService = matrixService;
    }

    @GetMapping("/route-operations/{id}/assignments")
    @PreAuthorize("hasAuthority('" + Permissions.MATRIX_VIEW + "')")
    public ResponseEntity<ApiResponse<List<AssignmentSchedulesDTO>>> getAssignmentsByRouteOperation(@PathVariable Integer id){
        return ResponseEntity.ok(ApiResponse.ok(matrixService.getAssignmentSchedules(id))) ;
    }
}
