package com.example.tgs_dev.controller.request.admin;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for updating a user via the SUPER_ADMIN admin API.
 *
 * <p>Role and active status are always required.
 * {@code newPassword} is optional: when {@code null} the current password is preserved;
 * when provided it must be at least 8 characters and will be BCrypt-encoded.
 *
 * <p>To change personal details (firstName, documentNumber, etc.), use the regular
 * person endpoint scoped to the user's company.
 */
public record UpdateAdminUserRequest(

        /** New role to assign. Must NOT be the SUPER_ADMIN role. */
        @NotNull Integer roleId,

        @NotNull Boolean active,

        /** Optional: provide to reset the password. Null means "keep existing". */
        @Nullable @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
        String newPassword

) {}
