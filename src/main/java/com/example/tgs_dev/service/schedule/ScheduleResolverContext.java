package com.example.tgs_dev.service.schedule;

import com.example.tgs_dev.entity.Route;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Immutable context passed down both resolver chains
 * ({@link DurationResolver} and {@link HeadwayResolver}) on each call.
 *
 * <p>This unified context lets both chains share the same snapshot of the
 * resolved period configuration for a given departure time, avoiding redundant
 * construction.
 *
 * <h3>Fields</h3>
 * <ul>
 *   <li>{@code route} — the route being scheduled.  Seasonal and
 *       calendar-override resolvers read the route's patterns and overrides.</li>
 *   <li>{@code departureTime} — the candidate departure time of the current
 *       slot (used for time-range matching).</li>
 *   <li>{@code operationDate} — the calendar date of the operation.</li>
 *   <li>{@code effectiveBaseDuration} — fallback trip duration (minutes)
 *       sourced from the active {@link com.example.tgs_dev.entity.RouteOperationalPeriod}.
 *       Used by {@link FixedDurationResolver} when no range matches.</li>
 *   <li>{@code effectiveDefaultHeadway} — fallback headway (minutes)
 *       sourced from the active period.
 *       Used by {@link FixedHeadwayResolver} when no range matches.</li>
 *   <li>{@code effectiveTimeRanges} — pre-resolved time-range lookups for this
 *       operation date.  Empty when {@code period.useTimeRanges = false}.</li>
 * </ul>
 *
 * <p>All fields are sourced from the active period before the resolver chains
 * are entered.  {@link com.example.tgs_dev.service.schedule.DepartureSlotGenerator}
 * is responsible for building this context on each slot iteration.
 */
public record ScheduleResolverContext(
        Route                 route,
        LocalTime             departureTime,
        LocalDate             operationDate,
        int                   effectiveBaseDuration,
        int                   effectiveDefaultHeadway,
        List<TimeRangeLookup> effectiveTimeRanges
) {}
