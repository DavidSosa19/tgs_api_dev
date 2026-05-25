package com.example.tgs_dev.controller.admin;

import com.example.tgs_dev.controller.request.admin.CreateCompanyRequest;
import com.example.tgs_dev.controller.request.admin.UpdateCompanyRequest;
import com.example.tgs_dev.controller.response.ApiResponse;
import com.example.tgs_dev.controller.response.admin.CompanyAdminDTO;
import com.example.tgs_dev.security.Permissions;
import com.example.tgs_dev.service.admin.AdminCompanyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for cross-tenant company management.
 *
 * <p>Access control layers:
 * <ol>
 *   <li>Path-level: {@code SecurityConfig} requires {@code SUPER_ADMIN_ACCESS} for
 *       all {@code /api/admin/**} paths.</li>
 *   <li>Method-level: {@code @PreAuthorize} below enforces the specific CRUD permission
 *       for each operation.</li>
 *   <li>Service-level: {@code AdminCompanyService.assertSuperAdmin()} defends against
 *       direct service invocations bypassing this controller.</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/admin/companies")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + Permissions.SUPER_ADMIN_ACCESS + "')")
public class AdminCompanyController {

    private final AdminCompanyService adminCompanyService;

    @GetMapping
    @PreAuthorize("hasAuthority('" + Permissions.COMPANY_READ + "')")
    public ResponseEntity<ApiResponse<List<CompanyAdminDTO>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok(adminCompanyService.findAll()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.COMPANY_READ + "')")
    public ResponseEntity<ApiResponse<CompanyAdminDTO>> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.ok(adminCompanyService.findById(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + Permissions.COMPANY_WRITE + "')")
    public ResponseEntity<ApiResponse<CompanyAdminDTO>> create(
            @RequestBody @Valid CreateCompanyRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok("company.created.success", adminCompanyService.create(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.COMPANY_WRITE + "')")
    public ResponseEntity<ApiResponse<CompanyAdminDTO>> update(
            @PathVariable Integer id,
            @RequestBody @Valid UpdateCompanyRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok("company.updated.success", adminCompanyService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.COMPANY_DEACTIVATE + "')")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Integer id) {
        adminCompanyService.deactivate(id);
        return ResponseEntity.ok(ApiResponse.ok("company.deactivated.success", null));
    }

    @PatchMapping("/{id}/reactivate")
    @PreAuthorize("hasAuthority('" + Permissions.COMPANY_WRITE + "')")
    public ResponseEntity<ApiResponse<Void>> reactivate(@PathVariable Integer id) {
        adminCompanyService.reactivate(id);
        return ResponseEntity.ok(ApiResponse.ok("admin.company.reactivated", null));
    }
}
