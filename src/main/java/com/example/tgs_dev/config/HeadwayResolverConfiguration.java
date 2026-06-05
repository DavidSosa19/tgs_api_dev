package com.example.tgs_dev.config;

import com.example.tgs_dev.service.schedule.FixedHeadwayResolver;
import com.example.tgs_dev.service.schedule.HeadwayResolver;
import com.example.tgs_dev.service.schedule.TimeRangeHeadwayResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the {@link HeadwayResolver} chain of responsibility and exposes it as a
 * single Spring bean for injection into
 * {@link com.example.tgs_dev.service.schedule.DepartureSlotGenerator}.
 *
 * <h3>Chain (highest → lowest priority)</h3>
 * <pre>
 *   TimeRangeHeadwayResolver   ← period's own time-range headway config
 *     → FixedHeadwayResolver   ← period's defaultHeadwayMinutes (always resolves)
 * </pre>
 *
 * <h3>Planned extensions</h3>
 * Future nodes (seasonal headway, calendar-override headway) can be inserted
 * above {@code TimeRangeHeadwayResolver} following the same pattern used in
 * {@link DurationResolverConfiguration}.  No existing node needs to change:
 *
 * <pre>
 *   CalendarOverrideHeadwayResolver  (future)
 *     → SeasonalHeadwayResolver      (future)
 *         → TimeRangeHeadwayResolver
 *             → FixedHeadwayResolver
 * </pre>
 *
 * <h3>Extending the chain</h3>
 * <ol>
 *   <li>Implement {@link HeadwayResolver}.</li>
 *   <li>Inject any required service in its constructor.</li>
 *   <li>Insert it at the desired priority position below.</li>
 * </ol>
 * No other class needs to change.
 */
@Configuration
public class HeadwayResolverConfiguration {

    @Bean
    public HeadwayResolver headwayResolver() {
        // Build chain from leaf (lowest priority) upward.
        HeadwayResolver fixed     = new FixedHeadwayResolver();
        return new TimeRangeHeadwayResolver(fixed); // head of chain — injected into DepartureSlotGenerator
    }
}
