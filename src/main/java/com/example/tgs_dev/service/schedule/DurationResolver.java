package com.example.tgs_dev.service.schedule;

/**
 * Strategy interface for resolving the departure-gap duration (in minutes) to
 * apply between consecutive schedule iterations.
 *
 * <h3>Chain of Responsibility</h3>
 * Implementations form a prioritised chain.  When a resolver cannot determine
 * the duration for a given context, it must delegate to the {@code next} resolver
 * in the chain rather than returning a default value directly.  The leaf node
 * ({@link FixedDurationResolver}) always produces a result and never delegates.
 *
 * <pre>
 *   SeasonalDurationResolver
 *     ↓ (no seasonal match)
 *   CalendarOverrideDurationResolver
 *     ↓ (no override for this date)
 *   TimeRangeDurationResolver
 *     ↓ (useTimeRanges=false, or no range matches)
 *   FixedDurationResolver          ← always resolves
 * </pre>
 *
 * <h3>Contract</h3>
 * <ul>
 *   <li>Must return a strictly positive value ({@code > 0}).</li>
 *   <li>Must never return {@code 0} or negative — callers do not guard against
 *       infinite loops caused by zero-duration increments.</li>
 *   <li>Must be thread-safe; implementations should be stateless beans.</li>
 * </ul>
 *
 * @see FixedDurationResolver
 * @see TimeRangeDurationResolver
 * @see CalendarOverrideDurationResolver
 * @see SeasonalDurationResolver
 */
public interface DurationResolver {

    /**
     * Returns the trip duration in minutes for the given departure slot.
     *
     * @param context  the unified resolver context carrying route, departure time, date,
     *                 and pre-resolved period configuration.
     * @return duration in minutes; strictly positive.
     */
    int resolve(ScheduleResolverContext context);
}
