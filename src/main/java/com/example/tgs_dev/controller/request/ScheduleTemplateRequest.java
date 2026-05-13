package com.example.tgs_dev.controller.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

public record ScheduleTemplateRequest(
        @NotNull Integer routeId,
        Integer secondaryRouteId,
        @NotBlank String templateNumber,
        @NotBlank String name,
        @NotNull LocalTime startTime,
        Boolean active
) {}
