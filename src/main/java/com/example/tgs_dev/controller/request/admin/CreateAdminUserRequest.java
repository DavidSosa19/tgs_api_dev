package com.example.tgs_dev.controller.request.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Request body for creating a user via the SUPER_ADMIN admin API.
 *
 * <p>Person resolution strategy:
 * <ul>
 *   <li>If {@code personId} is provided: the existing person is linked to the new user.
 *       The person must belong to {@code companyId}.</li>
 *   <li>If {@code personId} is {@code null}: a new person is created inline using
 *       {@code documentNumber}, {@code firstName} and {@code firstLastName}.</li>
 * </ul>
 *
 * <p>SUPER_ADMIN role cannot be assigned via this endpoint — use DB migrations instead.
 */
@NullMarked
public record CreateAdminUserRequest(

        @NotNull Integer companyId,

        @NotBlank String userName,

        @NotBlank @Size(min = 8) String password,

        /** ID of the role to assign. Must NOT be the SUPER_ADMIN role. */
        @NotNull Integer roleId,

        // ── Person resolution ──────────────────────────────────────────────────

        /** Link an existing person by ID (company-scoped). Null = create inline. */
        @Nullable Integer personId,

        /** Required when personId is null. */
        @Nullable String documentNumber,

        /** Required when personId is null. */
        @Nullable String firstName,

        @Nullable String secondName,

        /** Required when personId is null. */
        @Nullable String firstLastName,

        @Nullable String secondLastName
) {}
