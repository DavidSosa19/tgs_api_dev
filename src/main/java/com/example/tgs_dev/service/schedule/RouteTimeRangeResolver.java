package com.example.tgs_dev.service.schedule;

import java.time.LocalTime;
import java.util.List;
import java.util.OptionalInt;

/**
 * Pure domain utility for resolving which {@link TimeRangeLookup} applies to a
 * given departure time and returning its {@code durationMinutes}.
 *
 * <p>This class has <strong>no Spring dependency, no state, and no side effects</strong>.
 * It is a set of pure functions and should be tested directly without any mocking
 * infrastructure.  All JPA-aware callers ({@link TimeRangeDurationResolver},
 * {@link CalendarOverrideDurationResolver}, {@link SeasonalDurationResolver}) delegate
 * the actual matching arithmetic to this class.
 *
 * <h3>Matching semantics</h3>
 * Each range uses a half-open interval {@code [rangeStart, rangeEnd)} — see
 * {@link TimeRangeLookup} for the full specification.
 *
 * <h3>Input contract</h3>
 * <ul>
 *   <li>{@code ranges} must be sorted ascending by {@code rangeStart} (guaranteed by
 *       the service layer via {@code sortOrder}).  The resolver relies on this ordering
 *       only to find the <em>first</em> match efficiently; correctness is unaffected by
 *       unsorted input because the matching predicate does not depend on position.</li>
 *   <li>Ranges must not overlap — the service layer enforces this before persisting.</li>
 *   <li>An empty list is valid; {@link OptionalInt#empty()} is returned.</li>
 * </ul>
 */
public final class RouteTimeRangeResolver {

    private RouteTimeRangeResolver() {
        // utility class
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Finds the first range in {@code ranges} that contains {@code departureTime}
     * and returns its {@code durationMinutes}.
     *
     * @param departureTime  the time of the slot whose duration must be resolved.
     * @param ranges         sorted, non-overlapping list of candidate ranges.
     * @return the matched duration, or {@link OptionalInt#empty()} when no range matches.
     */
    public static OptionalInt resolve(LocalTime departureTime, List<TimeRangeLookup> ranges) {
        if (ranges == null || ranges.isEmpty()) return OptionalInt.empty();

        for (TimeRangeLookup range : ranges) {
            if (matches(range, departureTime)) {
                return OptionalInt.of(range.durationMinutes());
            }
        }
        return OptionalInt.empty();
    }

    /**
     * Finds the first range in {@code ranges} that contains {@code departureTime}
     * <strong>and</strong> has a positive {@code headwayMinutes} value, then returns
     * that headway.
     *
     * <p>Ranges with {@code headwayMinutes == 0} are treated as "no headway override
     * defined" and are skipped — this allows seasonal-pattern and calendar-override
     * lookups (which carry no headway data) to be present in the list without
     * accidentally returning zero as a headway.  The caller's resolver chain will
     * fall through to {@link FixedHeadwayResolver} in that case.
     *
     * @param departureTime  the time of the slot whose headway must be resolved.
     * @param ranges         sorted, non-overlapping list of candidate ranges.
     * @return the matched headway, or {@link OptionalInt#empty()} when no range
     *         with a positive headway matches.
     */
    public static OptionalInt resolveHeadway(LocalTime departureTime, List<TimeRangeLookup> ranges) {
        if (ranges == null || ranges.isEmpty()) return OptionalInt.empty();

        for (TimeRangeLookup range : ranges) {
            if (matches(range, departureTime) && range.headwayMinutes() > 0) {
                return OptionalInt.of(range.headwayMinutes());
            }
        }
        return OptionalInt.empty();
    }

    // ── Matching predicate ────────────────────────────────────────────────────

    /**
     * Returns {@code true} when {@code time} falls inside {@code range}.
     *
     * <p>For a regular (non-overnight) range {@code [start, end)}:
     * <pre>  start <= time < end</pre>
     *
     * <p>For an overnight range where {@code crossesMidnight = true}
     * (e.g. {@code 22:00 – 02:00}):
     * <pre>  time >= start  OR  time < end</pre>
     * A time like {@code 23:30} satisfies {@code >= 22:00}; a time like {@code 01:00}
     * satisfies {@code < 02:00}.  Both are correctly classified as belonging to the
     * overnight window.
     */
    static boolean matches(TimeRangeLookup range, LocalTime time) {
        LocalTime start = range.rangeStart();
        LocalTime end   = range.rangeEnd();

        if (!range.crossesMidnight()) {
            // Standard [start, end)
            return !time.isBefore(start) && time.isBefore(end);
        } else {
            // Overnight: [start, midnight) ∪ [midnight, end)
            // Equivalent to: time >= start  OR  time < end
            return !time.isBefore(start) || time.isBefore(end);
        }
    }
}
