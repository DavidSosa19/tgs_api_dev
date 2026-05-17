package com.example.tgs_dev.security;

/**
 * Constantes de nombres de roles.
 *
 * Cada constante coincide con el campo 'name' de core.app_role.
 * Agregar un rol: declarar la constante aquí + INSERT en core.app_role
 *                 + asignar permisos en core.role_permission.
 *
 * Los roles ya no están hardcodeados en Spring Security — el control
 * granular se hace mediante permisos individuales en @PreAuthorize.
 */
public final class AppRole {

    private AppRole() {}

    public static final String ADMIN = "ADMIN";
    public static final String USER  = "USER";
}
