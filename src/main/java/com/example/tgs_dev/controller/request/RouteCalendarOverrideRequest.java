package com.example.tgs_dev.controller.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * Request payload for creating or replacing a route calendar override.
 *
 * @param overrideDate    the specific date this override applies to.
 * @param useTimeRanges   when {@code true}, {@code ranges} must have 2–10 entries;
 *                        when {@code false}, {@code baseDuration} is used as-is.
 * @param baseDuration    fixed duration (or fallback when ranges have gaps); must be >= 1.
 * @param ranges          time-range entries; required (2–10) when {@code useTimeRanges = true}.
 */
public record RouteCalendarOverrideRequest(
        @NotNull LocalDate overrideDate,
        @NotNull Boolean   useTimeRanges,
        @NotNull @Min(1) Integer baseDuration,
        @Valid @Size(max = 10) List<RouteTimeRangeRequest> ranges
) {}
