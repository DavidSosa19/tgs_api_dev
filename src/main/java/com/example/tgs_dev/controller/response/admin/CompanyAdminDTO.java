package com.example.tgs_dev.controller.response.admin;

/**
 * Company projection used in the SUPER_ADMIN admin API responses.
 */
public record CompanyAdminDTO(
        Integer id,
        String  name,
        String  nit,
        Boolean active
) {}
