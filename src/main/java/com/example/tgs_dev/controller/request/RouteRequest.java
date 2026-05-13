package com.example.tgs_dev.controller.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record RouteRequest(
        @NotBlank String routeNumber,
        @NotNull @Positive Integer baseDuration,
        @NotNull @Positive Integer cycleCount
) {}
