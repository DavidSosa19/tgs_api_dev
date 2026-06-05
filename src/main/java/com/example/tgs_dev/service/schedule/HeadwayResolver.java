package com.example.tgs_dev.service.schedule;

/**
 * Strategy interface for resolving the headway (in minutes) between consecutive
 * departure slots for a given {@link ScheduleResolverContext}.
 *
 * <h3>Chain of Responsibility</h3>
 * Implementations form a prioritised chain.  When a resolver cannot determine the
 * headway for the given context it must delegate to the {@code next} resolver rather
 * than returning a default value directly.  The leaf node ({@link FixedHeadwayResolver})
 * always produces a result and never delegates.
 *
 * <pre>
 *   TimeRangeHeadwayResolver
 *     ↓ (no range with positive headway matches)
 *   FixedHeadwayResolver          ← always resolves
 * </pre>
 *
 * <p>Future resolvers (seasonal headway, calendar-override headway) can be inserted
 * above {@link TimeRangeHeadwayResolver} without changing any existing node.
 *
 * <h3>Contract</h3>
 * <ul>
 *   <li>Must return a strictly positive value ({@code > 0}).</li>
 *   <li>Must never return {@code 0} or negative — {@link DepartureSlotGenerator}
 *       enforces this post-condition and throws {@code IllegalStateException}
 *       if the contract is violated.</li>
 *   <li>Must be thread-safe; implementations should be stateless.</li>
 * </ul>
 *
 * @see FixedHeadwayResolver
 * @see TimeRangeHeadwayResolver
 */
public interface HeadwayResolver {

    /**
     * Returns the headway in minutes to apply after the slot described by
     * {@code context} when generating the next departure slot.
     *
     * @param context  the unified resolver context for the current departure slot.
     * @return headway in minutes; strictly positive.
     */
    int resolve(ScheduleResolverContext context);
}
