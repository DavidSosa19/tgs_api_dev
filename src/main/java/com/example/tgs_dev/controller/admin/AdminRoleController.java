package com.example.tgs_dev.controller.admin;

import com.example.tgs_dev.controller.response.ApiResponse;
import com.example.tgs_dev.controller.response.admin.RoleDTO;
import com.example.tgs_dev.repository.AppRoleRepository;
import com.example.tgs_dev.security.Permissions;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only endpoint that exposes the assignable roles for the user management UI.
 *
 * <p>SUPER_ADMIN is intentionally excluded — it cannot be granted through
 * the admin panel.
 */
@RestController
@RequestMapping("/api/admin/roles")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + Permissions.SUPER_ADMIN_ACCESS + "')")
public class AdminRoleController {

    private final AppRoleRepository appRoleRepository;

    @GetMapping
    @PreAuthorize("hasAuthority('" + Permissions.ADMIN_USER_READ + "')")
    public ResponseEntity<ApiResponse<List<RoleDTO>>> getAssignable() {
        List<RoleDTO> roles = appRoleRepository.findAssignable()
                .stream()
                .map(r -> new RoleDTO(r.getId(), r.getName(), r.getDescription()))
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(roles));
    }
}
