package com.example.tgs_dev.config;

import com.example.tgs_dev.service.RouteCalendarOverrideService;
import com.example.tgs_dev.service.SeasonalPatternService;
import com.example.tgs_dev.service.schedule.CalendarOverrideDurationResolver;
import com.example.tgs_dev.service.schedule.DurationResolver;
import com.example.tgs_dev.service.schedule.FixedDurationResolver;
import com.example.tgs_dev.service.schedule.SeasonalDurationResolver;
import com.example.tgs_dev.service.schedule.TimeRangeDurationResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the {@link DurationResolver} chain of responsibility and exposes it
 * as a single Spring bean for injection into {@link com.example.tgs_dev.service.ScheduleService}.
 *
 * <h3>Chain (highest → lowest priority)</h3>
 * <pre>
 *   CalendarOverrideDurationResolver   ← specific-date exception wins over everything
 *     → SeasonalDurationResolver       ← broad seasonal pattern (vacaciones, año escolar…)
 *         → TimeRangeDurationResolver  ← period time-range config
 *             → FixedDurationResolver  ← period baseDuration (always resolves)
 * </pre>
 *
 * <h3>Priority rationale</h3>
 * A day-specific calendar override is more precise than a seasonal pattern, so it
 * must be evaluated first.  If no override exists for a given date the chain falls
 * through to the seasonal level; if no seasonal pattern covers the date either it
 * falls through to the period's own time-range / base-duration config.
 *
 * <h3>Extending the chain</h3>
 * To add a new rule (e.g. a "special-event" override):
 * <ol>
 *   <li>Implement {@link DurationResolver}.</li>
 *   <li>Inject any required service in its constructor.</li>
 *   <li>Insert it into the chain below at the desired priority position.</li>
 * </ol>
 * No other class needs to change.
 */
@Configuration
public class DurationResolverConfiguration {

    @Bean
    public DurationResolver durationResolver(
            RouteCalendarOverrideService overrideService,
            SeasonalPatternService       seasonalPatternService) {

        // Build chain from leaf (lowest priority) upward.
        DurationResolver fixed    = new FixedDurationResolver();
        DurationResolver timeRange = new TimeRangeDurationResolver(fixed);
        DurationResolver seasonal  = new SeasonalDurationResolver(seasonalPatternService, timeRange);
        return new CalendarOverrideDurationResolver(overrideService, seasonal); // head of chain — injected into ScheduleService
    }
}
