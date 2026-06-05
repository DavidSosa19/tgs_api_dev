package com.example.tgs_dev.service.schedule;

import java.time.LocalTime;

/**
 * Immutable value object representing a single time-range window used by
 * {@link RouteTimeRangeResolver}.
 *
 * <p>This record is the <em>lingua franca</em> of the resolver layer: all
 * range-bearing domain objects are converted to this type before being passed
 * to the resolver.  The resolver itself has no JPA dependency — it is a pure function.
 *
 * <h3>Two independent dimensions</h3>
 * <ul>
 *   <li>{@code durationMinutes} — how long a vehicle's trip takes when it departs
 *       within this window.  Used by the {@link DurationResolver} chain.</li>
 *   <li>{@code headwayMinutes} — the target spacing between consecutive departure
 *       <em>slots</em> when the current slot falls within this window.  Used by the
 *       {@link HeadwayResolver} chain.  A value of {@code 0} means "no headway
 *       override defined for this range"; the resolver will fall through to the
 *       period's {@code defaultHeadwayMinutes}.</li>
 * </ul>
 *
 * <h3>Boundary semantics</h3>
 * All matching uses the <strong>half-open interval {@code [rangeStart, rangeEnd)}</strong>:
 * <ul>
 *   <li>{@code rangeStart} is <em>inclusive</em>.</li>
 *   <li>{@code rangeEnd} is <em>exclusive</em>.</li>
 * </ul>
 *
 * <h3>Overnight ranges</h3>
 * When {@code crossesMidnight} is {@code true}, the interval wraps past midnight:
 * a time {@code t} matches when {@code t >= rangeStart || t < rangeEnd}.
 *
 * @param rangeStart       inclusive lower boundary.
 * @param rangeEnd         exclusive upper boundary.
 * @param durationMinutes  trip duration (minutes) while this range is active; must be > 0.
 * @param headwayMinutes   departure slot spacing (minutes) while this range is active;
 *                         {@code 0} means no headway override — resolver falls through.
 * @param crossesMidnight  when {@code true}, the range wraps past midnight.
 */
public record TimeRangeLookup(
        LocalTime rangeStart,
        LocalTime rangeEnd,
        int       durationMinutes,
        int       headwayMinutes,
        boolean   crossesMidnight
) {

    /**
     * Convenience factory for callers that only carry duration data
     * (seasonal patterns, calendar overrides) and have no headway to provide.
     * Sets {@code headwayMinutes = 0} — the headway resolver will treat this
     * as "no override" and fall through to the period default.
     */
    public static TimeRangeLookup durationOnly(LocalTime rangeStart, LocalTime rangeEnd,
                                                int durationMinutes, boolean crossesMidnight) {
        return new TimeRangeLookup(rangeStart, rangeEnd, durationMinutes, 0, crossesMidnight);
    }
}
