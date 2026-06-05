package com.example.tgs_dev.controller.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Request body for creating or updating a schedule template.
 *
 * <p>{@code routeId} and {@code secondaryRouteId} are
 * {@link com.example.tgs_dev.entity.RouteGroup} ids (stable business identity),
 * not the surrogate version ids.
 *
 * <p>{@code sequenceOrder} defines the vehicle's position in the fixed dispatch
 * order for its route.  The vehicle with order 1 always departs first, order 2
 * second, and so on.  Departure times are derived from the route's active
 * {@link com.example.tgs_dev.entity.RouteOperationalPeriod} anchor and headway —
 * not from this template directly.
 */
public record ScheduleTemplateRequest(
        @NotNull Long    routeId,
        Long             secondaryRouteId,
        @NotBlank String templateNumber,
        @NotBlank String name,
        @NotNull @Positive Integer sequenceOrder,
        Boolean active
) {}
