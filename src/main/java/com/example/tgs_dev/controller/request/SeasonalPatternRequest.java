package com.example.tgs_dev.controller.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * Request payload for creating or updating a seasonal pattern.
 *
 * @param name          human-readable label (e.g. "Summer 2024", "Holiday Season").
 * @param seasonFrom    first date of the pattern period (inclusive).
 * @param seasonTo      last date of the pattern period (inclusive); must be >= {@code seasonFrom}.
 * @param useTimeRanges when {@code true}, {@code ranges} must have 2–10 entries.
 * @param baseDuration  fixed duration or fallback for gaps; must be >= 1.
 * @param ranges        time-range entries; required (2–10) when {@code useTimeRanges = true}.
 */
public record SeasonalPatternRequest(
        @NotBlank String    name,
        @NotNull  LocalDate seasonFrom,
        @NotNull  LocalDate seasonTo,
        @NotNull  Boolean   useTimeRanges,
        @NotNull  @Min(1) Integer baseDuration,
        @Valid @Size(max = 10) List<RouteTimeRangeRequest> ranges
) {}
