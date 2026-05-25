package com.example.tgs_dev.service;

import com.example.tgs_dev.controller.exception.BusinessException;
import com.example.tgs_dev.controller.request.RouteTimeRangeRequest;

import java.util.List;

/**
 * Stateless validator for time-range lists.
 *
 * <p>Shared by {@link RouteService}, {@link RouteCalendarOverrideService}, and
 * {@link SeasonalPatternService} so the same rules apply consistently regardless
 * of which domain object owns the ranges.
 *
 * <h3>Rules enforced</h3>
 * <ol>
 *   <li><strong>Count</strong>: when active, the list must have 2–10 entries.</li>
 *   <li><strong>End &gt; Start</strong>: for non-overnight ranges, {@code rangeEnd}
 *       must be strictly after {@code rangeStart}.</li>
 *   <li><strong>No overlap</strong>: after sorting by {@code rangeStart}, consecutive
 *       non-overnight ranges must not overlap, i.e.
 *       {@code sorted[i].rangeEnd <= sorted[i+1].rangeStart}.</li>
 * </ol>
 *
 * <h3>Phase 2 — overnight ranges</h3>
 * For ranges with {@code crossesMidnight = true}, end &lt; start is valid.
 * Overlap detection for overnight ranges is deliberately relaxed in Phase 2 —
 * a full overnight overlap check (requiring modular arithmetic) is deferred to
 * a future iteration.  The service layer warns if two overnight ranges are present.
 */
public final class TimeRangeValidator {

    private TimeRangeValidator() {}

    private static final int MIN_RANGES = 2;
    private static final int MAX_RANGES = 10;

    /**
     * Validates a complete list of time-range requests.
     *
     * @param ranges     the list to validate; must not be {@code null}.
     * @param contextKey a short description of the owner (e.g. "route", "override")
     *                   for clearer error messages.
     * @throws BusinessException if any rule is violated.
     */
    public static void validate(List<RouteTimeRangeRequest> ranges, String contextKey) {
        if (ranges == null || ranges.isEmpty()) {
            throw new BusinessException("timeRanges.required|" + contextKey);
        }

        if (ranges.size() < MIN_RANGES) {
            throw new BusinessException("timeRanges.minTwo|" + contextKey);
        }
        if (ranges.size() > MAX_RANGES) {
            throw new BusinessException("timeRanges.maxTen|" + contextKey);
        }

        for (RouteTimeRangeRequest r : ranges) {
            validateSingleRange(r, contextKey);
        }

        validateNoOverlaps(ranges, contextKey);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static void validateSingleRange(RouteTimeRangeRequest r, String contextKey) {
        if (!r.crossesMidnight() && !r.rangeEnd().isAfter(r.rangeStart())) {
            throw new BusinessException(
                    "timeRanges.endBeforeStart|" + contextKey
                    + "|" + r.rangeStart() + "-" + r.rangeEnd());
        }
        // For overnight ranges, end < start is intentional — no extra check here.
    }

    private static void validateNoOverlaps(List<RouteTimeRangeRequest> ranges, String contextKey) {
        // Sort a copy by rangeStart for overlap detection — don't mutate the input.
        List<RouteTimeRangeRequest> sorted = ranges.stream()
                .filter(r -> !r.crossesMidnight())     // phase 2: skip overnight in overlap check
                .sorted((a, b) -> a.rangeStart().compareTo(b.rangeStart()))
                .toList();

        for (int i = 0; i < sorted.size() - 1; i++) {
            RouteTimeRangeRequest current = sorted.get(i);
            RouteTimeRangeRequest next    = sorted.get(i + 1);

            // Overlap condition: current.rangeEnd > next.rangeStart
            if (current.rangeEnd().isAfter(next.rangeStart())) {
                throw new BusinessException(
                        "timeRanges.overlap|" + contextKey
                        + "|" + current.rangeStart() + "-" + current.rangeEnd()
                        + " vs " + next.rangeStart() + "-" + next.rangeEnd());
            }
        }
    }
}
