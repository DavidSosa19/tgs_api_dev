package com.example.tgs_dev.service.schedule;

import com.example.tgs_dev.entity.Route;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Immutable context passed down the {@link DurationResolver} chain on each call.
 *
 * <p>Carries all the information a resolver may need:
 * <ul>
 *   <li>{@code route}                 — the route being scheduled.  Seasonal and
 *                                        calendar-override resolvers read the route's
 *                                        seasonal patterns and calendar overrides.</li>
 *   <li>{@code departureTime}         — the candidate departure time of the current
 *                                        iteration (used for time-range matching).</li>
 *   <li>{@code operationDate}         — the calendar date of the operation (used by
 *                                        {@link CalendarOverrideDurationResolver} and
 *                                        {@link SeasonalDurationResolver}).</li>
 *   <li>{@code effectiveBaseDuration} — the resolved base duration (minutes) for the
 *                                        operation date, sourced from the active
 *                                        {@link com.example.tgs_dev.entity.RouteOperationalPeriod}.
 *                                        Used by {@link FixedDurationResolver} as the
 *                                        terminal fallback when no time-range matches.</li>
 *   <li>{@code effectiveTimeRanges}   — the pre-resolved time-range lookups for this
 *                                        operation date, sourced from the period's own
 *                                        {@code timeRanges} list when
 *                                        {@code period.useTimeRanges = true}, or an
 *                                        empty list otherwise.  Consumed by
 *                                        {@link TimeRangeDurationResolver}.</li>
 * </ul>
 *
 * <p>All fields are sourced from the active
 * {@link com.example.tgs_dev.entity.RouteOperationalPeriod} before the resolver chain
 * is entered.  Production schedule generation must use the canonical constructor with
 * period-resolved values.
 */
public record DurationResolverContext(
        Route                 route,
        LocalTime             departureTime,
        LocalDate             operationDate,
        int                   effectiveBaseDuration,
        List<TimeRangeLookup> effectiveTimeRanges
) {}
