package com.example.tgs_dev.controller.response.admin;

import com.example.tgs_dev.controller.response.PersonDTO;

import java.util.List;

/**
 * User projection used in the SUPER_ADMIN admin API responses.
 * Includes the company context so the admin can identify which tenant the user belongs to.
 */
public record UserAdminDTO(
        Long       id,
        String     userName,
        List<String> roles,
        Boolean    active,
        Integer    companyId,
        String     companyName,
        PersonDTO  person
) {}
