package com.example.tgs_dev.security;

/**
 * Catálogo de permisos del sistema.
 *
 * Cada constante corresponde exactamente al campo 'name' en la tabla core.permission.
 * Agregar un permiso: declarar la constante aquí + INSERT en core.permission
 *                     + asignar a los roles correspondientes en core.role_permission.
 *
 * Se usan en @PreAuthorize("hasAuthority('" + Permissions.ROUTE_READ + "')").
 */
public final class Permissions {

    private Permissions() {}

    // ── Person ────────────────────────────────────────────────────────────────
    public static final String PERSON_READ  = "PERSON_READ";
    public static final String PERSON_WRITE = "PERSON_WRITE";

    // ── Route ─────────────────────────────────────────────────────────────────
    public static final String ROUTE_READ  = "ROUTE_READ";
    public static final String ROUTE_WRITE = "ROUTE_WRITE";

    // ── Vehicle ───────────────────────────────────────────────────────────────
    public static final String VEHICLE_READ  = "VEHICLE_READ";
    public static final String VEHICLE_WRITE = "VEHICLE_WRITE";

    // ── Schedule template ─────────────────────────────────────────────────────
    public static final String SCHEDULE_TEMPLATE_READ  = "SCHEDULE_TEMPLATE_READ";
    public static final String SCHEDULE_TEMPLATE_WRITE = "SCHEDULE_TEMPLATE_WRITE";

    // ── Vehicle rotation ──────────────────────────────────────────────────────
    public static final String ROTATION_READ  = "ROTATION_READ";
    public static final String ROTATION_WRITE = "ROTATION_WRITE";

    // ── Operations ────────────────────────────────────────────────────────────
    public static final String OPERATION_READ       = "OPERATION_READ";
    public static final String OPERATION_MANAGE     = "OPERATION_MANAGE";
    /** Read access to the original (init-time) operation plan and audit views. */
    public static final String OPERATION_AUDIT_READ = "OPERATION_AUDIT_READ";

    // ── Users ─────────────────────────────────────────────────────────────────
    public static final String USER_READ   = "USER_READ";
    public static final String USER_MANAGE = "USER_MANAGE";

    // ── Matrix viewer ─────────────────────────────────────────────────────────
    public static final String MATRIX_VIEW = "MATRIX_VIEW";

    // ── Super-admin (system-level, cross-tenant) ───────────────────────────────
    /** Master gate: grants access to all /api/admin/** endpoints. */
    public static final String SUPER_ADMIN_ACCESS    = "SUPER_ADMIN_ACCESS";
    public static final String COMPANY_READ          = "COMPANY_READ";
    public static final String COMPANY_WRITE         = "COMPANY_WRITE";
    public static final String COMPANY_DEACTIVATE    = "COMPANY_DEACTIVATE";
    public static final String ADMIN_USER_READ       = "ADMIN_USER_READ";
    public static final String ADMIN_USER_WRITE      = "ADMIN_USER_WRITE";
}
