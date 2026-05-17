package com.example.tgs_dev.controller.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank String userName,
        @NotBlank @Size(min = 8) String password,
        @Valid @NotNull PersonRequest person,
        @NotNull @Positive Integer companyId
) {}
