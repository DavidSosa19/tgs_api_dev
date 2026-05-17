package com.example.tgs_dev.controller.request.admin;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for creating a new company via the SUPER_ADMIN admin API.
 *
 * @param name Company display name — must be unique.
 * @param nit  Tax identification number — must be unique.
 */
public record CreateCompanyRequest(
        @NotBlank String name,
        @NotBlank String nit
) {}
