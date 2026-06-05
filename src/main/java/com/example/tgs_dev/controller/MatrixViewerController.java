package com.example.tgs_dev.controller;

import com.example.tgs_dev.controller.response.ApiResponse;
import com.example.tgs_dev.controller.response.AssignmentSchedulesDTO;
import com.example.tgs_dev.controller.response.viewer.OperationScheduleDTO;
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

    /**
     * Returns the full operation schedule — header, vehicles, and departures —
     * structured for direct frontend rendering with no additional client-side
     * grouping or sorting required.
     *
     * <p>Fires exactly three SQL queries (operation lookup, assignment JOIN FETCH,
     * schedule batch projection). See {@link MatrixService#getOperationSchedules}
     * for the Redis caching hook point.
     *
     * @param id the route operation ID
     * @return {@code 200} with the schedule DTO; {@code 404} if the operation
     *         does not exist for the current tenant
     */
    @GetMapping("/route-operations/{id}/schedules")
    @PreAuthorize("hasAuthority('" + Permissions.MATRIX_VIEW + "')")
    public ResponseEntity<ApiResponse<OperationScheduleDTO>> getOperationSchedules(
            @PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.ok(matrixService.getOperationSchedules(id)));
    }

    /**
     * Returns the operation's <strong>original plan</strong> — every assignment
     * that ever existed (including soft-deleted ones) paired with its
     * {@code ORIGINAL} schedules regardless of whether they were later
     * superseded by recalculations or replacements.
     *
     * <p>Used to audit "what was originally planned at init" and to render
     * historical context next to the current view.  Requires the audit
     * permission since the data set may include removed vehicles that the
     * operational view excludes.
     *
     * @param id the route operation ID
     * @return {@code 200} with the original schedule DTO; {@code 404} if the
     *         operation does not exist for the current tenant
     */
    @GetMapping("/route-operations/{id}/original-schedule")
    @PreAuthorize("hasAuthority('" + Permissions.OPERATION_AUDIT_READ + "')")
    public ResponseEntity<ApiResponse<OperationScheduleDTO>> getOriginalOperationSchedules(
            @PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.ok(matrixService.getOriginalOperationSchedules(id)));
    }

    /**
     * @deprecated Replaced by {@link #getOperationSchedules(Integer)}.
     *             Use {@code GET /route-operations/{id}/schedules} for new code.
     *             This endpoint will be removed in a future release.
     */
    @Deprecated(since = "next-sprint", forRemoval = true)
    @GetMapping("/route-operations/{id}/assignments")
    @PreAuthorize("hasAuthority('" + Permissions.MATRIX_VIEW + "')")
    public ResponseEntity<ApiResponse<List<AssignmentSchedulesDTO>>> getAssignmentsByRouteOperation(
            @PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.ok(matrixService.getAssignmentSchedules(id)));
    }
}
