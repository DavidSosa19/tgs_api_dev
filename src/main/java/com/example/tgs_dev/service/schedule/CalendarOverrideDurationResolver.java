package com.example.tgs_dev.service.schedule;

import com.example.tgs_dev.entity.RouteCalendarOverride;
import com.example.tgs_dev.entity.RouteCalendarOverrideRange;
import com.example.tgs_dev.service.RouteCalendarOverrideService;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * {@link DurationResolver} that evaluates date-specific calendar overrides.
 *
 * <h3>Behaviour</h3>
 * <ol>
 *   <li>Queries {@link RouteCalendarOverrideService} for an override matching the
 *       route and operation date.</li>
 *   <li>If no override exists → delegates to {@code next}
 *       (typically {@link TimeRangeDurationResolver}).</li>
 *   <li>If an override is found with {@code useTimeRanges = false} →
 *       returns {@code override.baseDuration} (fixed override).</li>
 *   <li>If an override is found with {@code useTimeRanges = true} →
 *       matches the departure time against the override's ranges.
 *       <ul>
 *         <li>Match found → returns matched duration.</li>
 *         <li>No match (gap) → returns {@code override.baseDuration} as fallback.
 *             The route's own configuration is intentionally <em>not</em> consulted
 *             when an override exists — the override wholly replaces the route
 *             default for that date.</li>
 *       </ul>
 *   </li>
 * </ol>
 *
 * <h3>Design note — isolation</h3>
 * When a calendar override is active, it completely replaces the route's own
 * time-range (or fixed) configuration for that date.  This prevents ambiguous
 * results where the override and the route default partially overlap.
 */
public final class CalendarOverrideDurationResolver implements DurationResolver {

    private final RouteCalendarOverrideService overrideService;
    private final DurationResolver             next;

    public CalendarOverrideDurationResolver(RouteCalendarOverrideService overrideService,
                                            DurationResolver next) {
        this.overrideService = overrideService;
        this.next            = next;
    }

    @Override
    public int resolve(ScheduleResolverContext context) {
        Optional<RouteCalendarOverride> maybeOverride =
                overrideService.findByRouteAndDate(context.route(), context.operationDate());

        if (maybeOverride.isEmpty()) {
            return next.resolve(context);
        }

        RouteCalendarOverride override = maybeOverride.get();

        if (!Boolean.TRUE.equals(override.getUseTimeRanges())) {
            // Fixed duration override for this date.
            return override.getBaseDuration();
        }

        // Time-range override — find matching window.
        List<TimeRangeLookup> lookups = toLookups(override.getRanges());
        OptionalInt result = RouteTimeRangeResolver.resolve(context.departureTime(), lookups);

        // Fallback to override's own baseDuration, not the route's, to keep
        // the override's intent intact for the entire day.
        return result.isPresent() ? result.getAsInt() : override.getBaseDuration();
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private static List<TimeRangeLookup> toLookups(List<RouteCalendarOverrideRange> ranges) {
        if (ranges == null) return List.of();
        // Calendar overrides carry duration data only — headwayMinutes = 0 signals
        // "no headway override here" to RouteTimeRangeResolver.resolveHeadway().
        return ranges.stream()
                .map(r -> TimeRangeLookup.durationOnly(
                        r.getRangeStart(),
                        r.getRangeEnd(),
                        r.getDurationMinutes(),
                        r.isCrossesMidnight()))
                .toList();
    }
}
