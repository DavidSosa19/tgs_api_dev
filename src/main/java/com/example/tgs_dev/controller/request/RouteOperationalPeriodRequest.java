package com.example.tgs_dev.controller.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * Request body for creating or updating a {@link com.example.tgs_dev.entity.RouteOperationalPeriod}.
 *
 * <h3>Nullable fields</h3>
 * <ul>
 *   <li>{@code effectiveTo} — {@code null} means an open-ended period with no planned end date.</li>
 *   <li>{@code useTimeRanges} — {@code null} is treated as {@code false} by the service layer.</li>
 *   <li>{@code timeRanges} — only evaluated when {@code useTimeRanges = true}; must contain
 *       2–10 non-overlapping entries in that case (validated by
 *       {@link com.example.tgs_dev.service.TimeRangeValidator}).</li>
 * </ul>
 */
public record RouteOperationalPeriodRequest(
        @NotBlank @Size(max = 100) String label,
        @NotNull @Positive Integer baseDuration,
        @NotNull @Positive Integer cycleCount,
        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveTo,
        Boolean useTimeRanges,
        @Valid @Size(max = 10) List<RouteTimeRangeRequest> timeRanges
) {}
