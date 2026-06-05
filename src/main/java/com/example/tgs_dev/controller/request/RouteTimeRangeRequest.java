package com.example.tgs_dev.controller.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

/**
 * Request payload for a single time-range entry within a
 * {@link com.example.tgs_dev.entity.RouteOperationalPeriod}.
 *
 * <h3>Two independent dimensions</h3>
 * <ul>
 *   <li>{@code durationMinutes} — how long a vehicle's trip takes when it departs
 *       within {@code [rangeStart, rangeEnd)}.  Used by the duration resolver chain.</li>
 *   <li>{@code headwayMinutes} — target spacing between consecutive departure slots
 *       within this window.  Used by the headway resolver chain.</li>
 * </ul>
 *
 * <h3>Overnight ranges</h3>
 * When {@code crossesMidnight = true}, the service layer permits
 * {@code rangeEnd <= rangeStart}.  When {@code false} (default), it enforces
 * {@code rangeEnd > rangeStart}.
 *
 * @param rangeStart      inclusive lower boundary.
 * @param rangeEnd        exclusive upper boundary.
 * @param durationMinutes trip duration within this window; must be >= 1.
 * @param headwayMinutes  departure slot spacing within this window; must be >= 1.
 * @param crossesMidnight set to {@code true} for windows that span midnight.
 */
public record RouteTimeRangeRequest(
        @NotNull LocalTime rangeStart,
        @NotNull LocalTime rangeEnd,
        @NotNull @Min(1) Integer durationMinutes,
        @NotNull @Min(1) Integer headwayMinutes,
        boolean crossesMidnight
) {}
