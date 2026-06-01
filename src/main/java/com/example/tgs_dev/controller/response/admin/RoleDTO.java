package com.example.tgs_dev.controller.response.admin;

/**
 * Read-only projection of {@link com.example.tgs_dev.entity.AppRoleEntity}
 * used by the role selector in the admin user management UI.
 */
public record RoleDTO(Integer id, String name, String description) {}
