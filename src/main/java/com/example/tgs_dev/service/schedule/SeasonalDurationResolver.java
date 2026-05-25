package com.example.tgs_dev.service.schedule;

import com.example.tgs_dev.entity.SeasonalPattern;
import com.example.tgs_dev.entity.SeasonalPatternRange;
import com.example.tgs_dev.service.SeasonalPatternService;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Head node of the {@link DurationResolver} chain.
 *
 * <p>Evaluates named seasonal patterns before any calendar override or
 * route-level time-range configuration is consulted.
 *
 * <h3>Behaviour</h3>
 * <ol>
 *   <li>Queries {@link SeasonalPatternService} for the first active pattern that
 *       covers the operation date for the given route.  When multiple patterns
 *       overlap for the same date, the one with the lowest {@code id} wins
 *       (insertion-order determinism).</li>
 *   <li>If no seasonal pattern is active → delegates to {@code next}
 *       (typically {@link CalendarOverrideDurationResolver}).</li>
 *   <li>If a pattern is found with {@code useTimeRanges = false} →
 *       returns {@code pattern.baseDuration}.</li>
 *   <li>If a pattern is found with {@code useTimeRanges = true} →
 *       matches the departure time against the pattern's ranges.
 *       Falls back to {@code pattern.baseDuration} for gaps, exactly as
 *       {@link CalendarOverrideDurationResolver} does for overrides.</li>
 * </ol>
 *
 * <h3>Priority chain (complete)</h3>
 * <pre>
 *   SeasonalDurationResolver       ← this class (highest priority)
 *     ↓ no matching seasonal period
 *   CalendarOverrideDurationResolver
 *     ↓ no override for this date
 *   TimeRangeDurationResolver
 *     ↓ useTimeRanges=false or no range matches
 *   FixedDurationResolver          ← always resolves (lowest priority)
 * </pre>
 */
public final class SeasonalDurationResolver implements DurationResolver {

    private final SeasonalPatternService seasonalPatternService;
    private final DurationResolver       next;

    public SeasonalDurationResolver(SeasonalPatternService seasonalPatternService,
                                    DurationResolver next) {
        this.seasonalPatternService = seasonalPatternService;
        this.next                   = next;
    }

    @Override
    public int resolve(DurationResolverContext context) {
        Optional<SeasonalPattern> maybePattern =
                seasonalPatternService.findActivePatternForDate(
                        context.route(), context.operationDate());

        if (maybePattern.isEmpty()) {
            return next.resolve(context);
        }

        SeasonalPattern pattern = maybePattern.get();

        if (!Boolean.TRUE.equals(pattern.getUseTimeRanges())) {
            return pattern.getBaseDuration();
        }

        List<TimeRangeLookup> lookups = toLookups(pattern.getRanges());
        OptionalInt result = RouteTimeRangeResolver.resolve(context.departureTime(), lookups);

        return result.isPresent() ? result.getAsInt() : pattern.getBaseDuration();
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private static List<TimeRangeLookup> toLookups(List<SeasonalPatternRange> ranges) {
        if (ranges == null) return List.of();
        return ranges.stream()
                .map(r -> new TimeRangeLookup(
                        r.getRangeStart(),
                        r.getRangeEnd(),
                        r.getDurationMinutes(),
                        r.isCrossesMidnight()))
                .toList();
    }
}
