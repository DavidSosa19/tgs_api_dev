package com.example.tgs_dev.controller.request;

import jakarta.validation.constraints.NotBlank;

public record PersonRequest(
        @NotBlank String documentNumber,
        @NotBlank String firstName,
        String secondName,
        @NotBlank String firstLastName,
        String secondLastName,
        Boolean active
) {}
