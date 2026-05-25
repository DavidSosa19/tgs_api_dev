package com.example.tgs_dev.service.schedule;

import java.time.LocalTime;

/**
 * Immutable value object representing a single time-range window used by
 * {@link RouteTimeRangeResolver}.
 *
 * <p>This record is the <em>lingua franca</em> of the resolver layer: all three
 * range-bearing domain objects ({@code RouteTimeRange}, {@code RouteCalendarOverrideRange},
 * and {@code SeasonalPatternRange}) are converted to this type before being passed
 * to the resolver.  The resolver itself has no JPA dependency — it is a pure function.
 *
 * <h3>Boundary semantics</h3>
 * All matching uses the <strong>half-open interval {@code [rangeStart, rangeEnd)}</strong>:
 * <ul>
 *   <li>{@code rangeStart} is <em>inclusive</em> — a departure time exactly equal to
 *       {@code rangeStart} matches this range.</li>
 *   <li>{@code rangeEnd} is <em>exclusive</em> — a departure time exactly equal to
 *       {@code rangeEnd} does <em>not</em> match this range; it falls into the next
 *       range whose {@code rangeStart == rangeEnd} of this range, or into the gap.</li>
 * </ul>
 *
 * <h3>Overnight ranges (Phase 2)</h3>
 * When {@code crossesMidnight} is {@code true}, the interval wraps past midnight:
 * a time {@code t} matches when {@code t >= rangeStart || t < rangeEnd}.
 *
 * @param rangeStart       inclusive lower boundary.
 * @param rangeEnd         exclusive upper boundary (or upper boundary before midnight
 *                         when {@code crossesMidnight} is true).
 * @param durationMinutes  number of minutes to add between consecutive departure
 *                         times while this range is active.
 * @param crossesMidnight  when {@code true}, the range wraps past midnight.
 */
public record TimeRangeLookup(
        LocalTime rangeStart,
        LocalTime rangeEnd,
        int       durationMinutes,
        boolean   crossesMidnight
) {}
