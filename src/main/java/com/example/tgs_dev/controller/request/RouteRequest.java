package com.example.tgs_dev.controller.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for creating or updating a {@link com.example.tgs_dev.entity.Route}.
 *
 * <p>A route is the identity of a transit corridor.  All operational parameters
 * (cycle count, departure gaps, time ranges) are managed via
 * {@link RouteOperationalPeriodRequest} — not here.
 */
public record RouteRequest(@NotBlank String routeNumber) {}
