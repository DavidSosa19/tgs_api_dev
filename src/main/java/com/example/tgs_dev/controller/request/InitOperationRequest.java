package com.example.tgs_dev.controller.request;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Request to initialise one or all route operations for a given date.
 *
 * <p>Uses {@code routeId} instead of a raw {@code Route} entity to prevent
 * raw-entity deserialization (CWE-502/639): the controller resolves the route
 * through the tenant-scoped {@code RouteService.findById()} so a caller cannot
 * supply an arbitrary company's route ID and bypass tenant isolation.
 *
 * <p>{@code routeId} is nullable — when {@code null} the controller interprets
 * the request as "initialise <em>all</em> routes" (POST /all endpoint).
 */
public record InitOperationRequest(
        Integer routeId,
        @NotNull LocalDate date
) {
}
