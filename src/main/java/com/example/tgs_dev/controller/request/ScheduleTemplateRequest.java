package com.example.tgs_dev.controller.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

/**
 * Request body for creating or updating a schedule template.
 *
 * <p>{@code routeId} and {@code secondaryRouteId} are
 * {@link com.example.tgs_dev.entity.RouteGroup} ids (stable business identity),
 * not the surrogate version ids.
 */
public record ScheduleTemplateRequest(
        @NotNull Long   routeId,
        Long            secondaryRouteId,
        @NotBlank String templateNumber,
        @NotBlank String name,
        @NotNull LocalTime startTime,
        Boolean active
) {}
