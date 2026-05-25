package com.example.tgs_dev.controller.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

/**
 * Request payload for a single time-range entry within a route, calendar override,
 * or seasonal pattern.
 *
 * <h3>Overnight ranges (Phase 2)</h3>
 * When {@code crossesMidnight = true}, the service layer permits {@code rangeEnd <= rangeStart}.
 * When {@code false} (default), the service enforces {@code rangeEnd > rangeStart}.
 *
 * @param rangeStart      inclusive lower boundary.
 * @param rangeEnd        exclusive upper boundary.
 * @param durationMinutes departure gap to apply within this window; must be >= 1.
 * @param crossesMidnight set to {@code true} for windows that span midnight.
 */
public record RouteTimeRangeRequest(
        @NotNull LocalTime rangeStart,
        @NotNull LocalTime rangeEnd,
        @NotNull @Min(1) Integer durationMinutes,
        boolean crossesMidnight
) {}
