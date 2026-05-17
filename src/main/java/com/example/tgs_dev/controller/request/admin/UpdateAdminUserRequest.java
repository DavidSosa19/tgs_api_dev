package com.example.tgs_dev.controller.request.admin;

import jakarta.validation.constraints.NotNull;

/**
 * Request body for updating a user via the SUPER_ADMIN admin API.
 *
 * <p>Only role and active status are mutable via this endpoint.
 * To change personal details, use the regular person endpoint scoped to the company.
 */
public record UpdateAdminUserRequest(

        /** New role to assign. Must NOT be the SUPER_ADMIN role. */
        @NotNull Integer roleId,

        @NotNull Boolean active
) {}
