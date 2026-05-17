package com.example.tgs_dev.controller.request.admin;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for updating an existing company via the SUPER_ADMIN admin API.
 *
 * @param name New company display name.
 * @param nit  New tax identification number.
 */
public record UpdateCompanyRequest(
        @NotBlank String name,
        @NotBlank String nit
) {}
