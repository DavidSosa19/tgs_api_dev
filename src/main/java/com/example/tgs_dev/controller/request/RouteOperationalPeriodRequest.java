package com.example.tgs_dev.controller.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Request body for creating or updating a {@link com.example.tgs_dev.entity.RouteOperationalPeriod}.
 *
 * <h3>Scheduling parameters</h3>
 * <ul>
 *   <li>{@code firstDeparture} — time of the first slot of the day (e.g. {@code 05:30}).
 *       Together with {@code lastDeparture} and the headway config this fully determines
 *       the day's departure sequence.</li>
 *   <li>{@code lastDeparture} — time of the last permitted departure slot.</li>
 *   <li>{@code defaultHeadwayMinutes} — spacing between consecutive slots when no
 *       time-range overrides the headway.  Must be positive.</li>
 *   <li>{@code baseDuration} — typical trip duration (minutes).  Used for fleet-size
 *       guidance ({@code vehiclesNeeded = ceil(baseDuration / defaultHeadwayMinutes)})
 *       and by the {@link com.example.tgs_dev.service.schedule.DurationResolver} chain
 *       as the fixed fallback.  Must be positive.</li>
 * </ul>
 *
 * <h3>Nullable fields</h3>
 * <ul>
 *   <li>{@code effectiveTo} — {@code null} means an open-ended period.</li>
 *   <li>{@code useTimeRanges} — {@code null} treated as {@code false}.</li>
 *   <li>{@code timeRanges} — only evaluated when {@code useTimeRanges = true}; must
 *       contain 2–10 non-overlapping entries (validated by
 *       {@link com.example.tgs_dev.service.TimeRangeValidator}).</li>
 * </ul>
 */
public record RouteOperationalPeriodRequest(
        @NotBlank @Size(max = 100) String label,
        @NotNull @Positive Integer baseDuration,
        @NotNull LocalTime firstDeparture,
        @NotNull LocalTime lastDeparture,
        @NotNull @Positive Integer defaultHeadwayMinutes,
        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveTo,
        Boolean useTimeRanges,
        @Valid @Size(max = 10) List<RouteTimeRangeRequest> timeRanges
) {}
