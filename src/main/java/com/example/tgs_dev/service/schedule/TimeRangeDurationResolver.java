package com.example.tgs_dev.service.schedule;

import java.util.List;
import java.util.OptionalInt;

/**
 * {@link DurationResolver} that evaluates the pre-resolved time-range configuration
 * carried on {@link DurationResolverContext#effectiveTimeRanges()}.
 *
 * <h3>Behaviour</h3>
 * <ol>
 *   <li>If {@code context.effectiveTimeRanges()} is empty → delegates to {@code next}
 *       (typically {@link FixedDurationResolver}).  An empty list means either no
 *       time-range config exists for this operation date, or the active
 *       {@link com.example.tgs_dev.entity.RouteOperationalPeriod} has
 *       {@code useTimeRanges = false}.</li>
 *   <li>Delegates matching arithmetic to {@link RouteTimeRangeResolver}.</li>
 *   <li>If a match is found → returns the matched duration.</li>
 *   <li>If no range matches (gap, before first range, after last range) →
 *       delegates to {@code next}.</li>
 * </ol>
 *
 * <p>The caller ({@link com.example.tgs_dev.service.ScheduleService}) is responsible
 * for building the {@code effectiveTimeRanges} list from the active period before
 * entering the resolver chain.  This keeps all entity-to-lookup conversion in one
 * place and makes the resolver itself a pure function over the context.
 */
public final class TimeRangeDurationResolver implements DurationResolver {

    private final DurationResolver next;

    public TimeRangeDurationResolver(DurationResolver next) {
        this.next = next;
    }

    @Override
    public int resolve(DurationResolverContext context) {
        List<TimeRangeLookup> ranges = context.effectiveTimeRanges();
        if (ranges.isEmpty()) {
            return next.resolve(context);
        }

        OptionalInt result = RouteTimeRangeResolver.resolve(context.departureTime(), ranges);
        return result.isPresent() ? result.getAsInt() : next.resolve(context);
    }
}
