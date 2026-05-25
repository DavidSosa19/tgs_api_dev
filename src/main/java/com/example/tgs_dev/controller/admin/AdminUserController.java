package com.example.tgs_dev.controller.admin;

import com.example.tgs_dev.controller.request.admin.CreateAdminUserRequest;
import com.example.tgs_dev.controller.request.admin.UpdateAdminUserRequest;
import com.example.tgs_dev.controller.response.ApiResponse;
import com.example.tgs_dev.controller.response.admin.UserAdminDTO;
import com.example.tgs_dev.security.Permissions;
import com.example.tgs_dev.service.admin.AdminUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for cross-tenant user management.
 *
 * <p>Access control layers:
 * <ol>
 *   <li>Path-level: {@code SecurityConfig} requires {@code SUPER_ADMIN_ACCESS}.</li>
 *   <li>Method-level: {@code @PreAuthorize} enforces per-operation permissions.</li>
 *   <li>Service-level: {@code AdminUserService.assertSuperAdmin()} defends against
 *       direct service invocations.</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + Permissions.SUPER_ADMIN_ACCESS + "')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    @PreAuthorize("hasAuthority('" + Permissions.ADMIN_USER_READ + "')")
    public ResponseEntity<ApiResponse<List<UserAdminDTO>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok(adminUserService.findAll()));
    }

    @GetMapping("/company/{companyId}")
    @PreAuthorize("hasAuthority('" + Permissions.ADMIN_USER_READ + "')")
    public ResponseEntity<ApiResponse<List<UserAdminDTO>>> getByCompany(
            @PathVariable Integer companyId) {
        return ResponseEntity.ok(ApiResponse.ok(adminUserService.findByCompany(companyId)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.ADMIN_USER_READ + "')")
    public ResponseEntity<ApiResponse<UserAdminDTO>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(adminUserService.findById(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + Permissions.ADMIN_USER_WRITE + "')")
    public ResponseEntity<ApiResponse<UserAdminDTO>> create(
            @RequestBody @Valid CreateAdminUserRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok("adminUser.created.success", adminUserService.create(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.ADMIN_USER_WRITE + "')")
    public ResponseEntity<ApiResponse<UserAdminDTO>> update(
            @PathVariable Long id,
            @RequestBody @Valid UpdateAdminUserRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok("adminUser.updated.success", adminUserService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.ADMIN_USER_WRITE + "')")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id) {
        adminUserService.deactivate(id);
        return ResponseEntity.ok(ApiResponse.ok("adminUser.deactivated.success", null));
    }

    @PatchMapping("/{id}/reactivate")
    @PreAuthorize("hasAuthority('" + Permissions.ADMIN_USER_WRITE + "')")
    public ResponseEntity<ApiResponse<Void>> reactivate(@PathVariable Long id) {
        adminUserService.reactivate(id);
        return ResponseEntity.ok(ApiResponse.ok("admin.user.reactivated", null));
    }
}
