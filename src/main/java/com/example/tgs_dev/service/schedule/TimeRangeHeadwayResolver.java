package com.example.tgs_dev.service.schedule;

import java.util.List;
import java.util.OptionalInt;

/**
 * {@link HeadwayResolver} that evaluates the pre-resolved time-range configuration
 * carried on {@link ScheduleResolverContext#effectiveTimeRanges()}.
 *
 * <h3>Behaviour</h3>
 * <ol>
 *   <li>If {@code context.effectiveTimeRanges()} is empty → delegates to {@code next}
 *       (typically {@link FixedHeadwayResolver}).  An empty list means either no
 *       time-range config exists for this operation date, or the active
 *       {@link com.example.tgs_dev.entity.RouteOperationalPeriod} has
 *       {@code useTimeRanges = false}.</li>
 *   <li>Delegates matching to
 *       {@link RouteTimeRangeResolver#resolveHeadway(java.time.LocalTime, List)}.</li>
 *   <li>If a matching range with a positive headway is found → returns it.</li>
 *   <li>If no range with a positive headway matches → delegates to {@code next}.
 *       This covers gaps between ranges and ranges that carry {@code headwayMinutes = 0}
 *       (e.g. seasonal-pattern or calendar-override ranges that don't define headway).</li>
 * </ol>
 *
 * <p>The caller ({@link DepartureSlotGenerator}) is responsible for building the
 * {@code effectiveTimeRanges} list from the active period before entering the
 * resolver chain.
 */
public final class TimeRangeHeadwayResolver implements HeadwayResolver {

    private final HeadwayResolver next;

    public TimeRangeHeadwayResolver(HeadwayResolver next) {
        this.next = next;
    }

    @Override
    public int resolve(ScheduleResolverContext context) {
        List<TimeRangeLookup> ranges = context.effectiveTimeRanges();
        if (ranges.isEmpty()) {
            return next.resolve(context);
        }

        OptionalInt result = RouteTimeRangeResolver.resolveHeadway(
                context.departureTime(), ranges);

        return result.isPresent() ? result.getAsInt() : next.resolve(context);
    }
}
