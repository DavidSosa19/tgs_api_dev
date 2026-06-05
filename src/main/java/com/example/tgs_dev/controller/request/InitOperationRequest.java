package com.example.tgs_dev.controller.request;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Request to initialise one or all route operations for a given date.
 *
 * <p>Uses {@code routeGroupId} ({@link com.example.tgs_dev.entity.RouteGroup} id —
 * the stable SCD business identity) instead of the surrogate version id,
 * consistent with every other user-facing endpoint in the system.  The controller
 * resolves the route through the tenant-scoped
 * {@link com.example.tgs_dev.service.RouteService#findByGroupId} so a caller cannot
 * supply an arbitrary company's group id and bypass tenant isolation
 * (CWE-502/639).
 *
 * <p>{@code routeGroupId} is nullable — when {@code null} the controller interprets
 * the request as "initialise <em>all</em> routes" (POST /all endpoint).
 */
public record InitOperationRequest(
        Long              routeGroupId,
        @NotNull LocalDate date
) {
}
