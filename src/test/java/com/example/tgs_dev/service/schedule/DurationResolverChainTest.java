package com.example.tgs_dev.service.schedule;

import com.example.tgs_dev.entity.Route;
import com.example.tgs_dev.entity.RouteCalendarOverride;
import com.example.tgs_dev.entity.SeasonalPattern;
import com.example.tgs_dev.service.RouteCalendarOverrideService;
import com.example.tgs_dev.service.SeasonalPatternService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.example.tgs_dev.TestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Integration test for the full {@link DurationResolver} chain as wired in
 * {@link com.example.tgs_dev.config.DurationResolverConfiguration}.
 *
 * <h3>Chain under test</h3>
 * <pre>
 *   CalendarOverride → Seasonal → TimeRange → Fixed(baseDuration)
 * </pre>
 *
 * These tests verify the priority contract across resolvers — something the
 * individual unit tests (each with a mocked {@code next}) cannot cover.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DurationResolver — full chain priority")
class DurationResolverChainTest {

    @Mock RouteCalendarOverrideService overrideService;
    @Mock SeasonalPatternService       seasonalService;

    DurationResolver chain;

    private static final LocalDate DATE  = LocalDate.of(2024, 7, 15);
    private static final Route     ROUTE = route(1, "R1");

    @BeforeEach
    void buildChain() {
        DurationResolver fixed     = new FixedDurationResolver();
        DurationResolver timeRange = new TimeRangeDurationResolver(fixed);
        DurationResolver seasonal  = new SeasonalDurationResolver(seasonalService, timeRange);
        chain = new CalendarOverrideDurationResolver(overrideService, seasonal);
    }

    private DurationResolverContext ctx(LocalTime time, int baseDuration) {
        return new DurationResolverContext(ROUTE, time, DATE, baseDuration, List.of());
    }

    // ── Priority: CalendarOverride > Seasonal ─────────────────────────────────

    @Nested @DisplayName("CalendarOverride wins over Seasonal")
    class OverrideBeforeSeasonal {

        @Test @DisplayName("override duration is used even when a seasonal pattern is also active")
        void override_takesOver_seasonal() {
            RouteCalendarOverride ov = fixedOverride(20);
            when(overrideService.findByRouteAndDate(ROUTE, DATE)).thenReturn(Optional.of(ov));

            // Set up a seasonal pattern that would return 90 min — but it must never be reached.
            // lenient() because CalendarOverride resolves first and never calls next.
            lenient().when(seasonalService.findActivePatternForDate(ROUTE, DATE))
                     .thenReturn(Optional.of(fixedPattern(90)));

            assertThat(chain.resolve(ctx(LocalTime.of(8, 0), 30))).isEqualTo(20);
            // seasonal was never consulted
            verifyNoInteractions(seasonalService);
        }
    }

    // ── Priority: Seasonal > TimeRange/Fixed ─────────────────────────────────

    @Nested @DisplayName("Seasonal wins when no CalendarOverride exists")
    class SeasonalFallthrough {

        @Test @DisplayName("seasonal duration is used when no override exists for the date")
        void seasonal_usedWhen_noOverride() {
            when(overrideService.findByRouteAndDate(ROUTE, DATE)).thenReturn(Optional.empty());
            when(seasonalService.findActivePatternForDate(ROUTE, DATE))
                    .thenReturn(Optional.of(fixedPattern(55)));

            assertThat(chain.resolve(ctx(LocalTime.of(8, 0), 30))).isEqualTo(55);
        }
    }

    // ── Priority: TimeRange > Fixed ───────────────────────────────────────────

    @Nested @DisplayName("TimeRange wins when no Override and no Seasonal")
    class TimeRangeFallthrough {

        @Test @DisplayName("period time-range duration used when no override and no seasonal")
        void timeRange_usedWhen_noOverrideNoSeasonal() {
            when(overrideService.findByRouteAndDate(ROUTE, DATE)).thenReturn(Optional.empty());
            when(seasonalService.findActivePatternForDate(ROUTE, DATE)).thenReturn(Optional.empty());

            List<TimeRangeLookup> ranges = List.of(
                    lookup(LocalTime.of(6, 0), LocalTime.of(12, 0), 45));
            DurationResolverContext ctx = new DurationResolverContext(
                    ROUTE, LocalTime.of(8, 0), DATE, 30, ranges);

            assertThat(chain.resolve(ctx)).isEqualTo(45);
        }
    }

    // ── Priority: Fixed (baseDuration) as last resort ─────────────────────────

    @Nested @DisplayName("Fixed baseDuration is the final fallback")
    class FixedFallthrough {

        @Test @DisplayName("baseDuration used when nothing else matches")
        void fixed_usedWhen_nothingMatches() {
            when(overrideService.findByRouteAndDate(ROUTE, DATE)).thenReturn(Optional.empty());
            when(seasonalService.findActivePatternForDate(ROUTE, DATE)).thenReturn(Optional.empty());
            // no time ranges in context → effectiveTimeRanges is empty

            assertThat(chain.resolve(ctx(LocalTime.of(8, 0), 30))).isEqualTo(30);
        }

        @Test @DisplayName("baseDuration used when time falls outside all configured ranges")
        void fixed_usedWhen_timeOutsideRanges() {
            when(overrideService.findByRouteAndDate(ROUTE, DATE)).thenReturn(Optional.empty());
            when(seasonalService.findActivePatternForDate(ROUTE, DATE)).thenReturn(Optional.empty());

            // Range only covers 06:00–09:00; departure at 20:00 is outside
            List<TimeRangeLookup> ranges = List.of(
                    lookup(LocalTime.of(6, 0), LocalTime.of(9, 0), 120));
            DurationResolverContext ctx = new DurationResolverContext(
                    ROUTE, LocalTime.of(20, 0), DATE, 30, ranges);

            assertThat(chain.resolve(ctx)).isEqualTo(30);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private RouteCalendarOverride fixedOverride(int baseDuration) {
        RouteCalendarOverride ov = new RouteCalendarOverride();
        ov.setUseTimeRanges(false);
        ov.setBaseDuration(baseDuration);
        ov.setRanges(new ArrayList<>());
        return ov;
    }

    private SeasonalPattern fixedPattern(int baseDuration) {
        SeasonalPattern p = new SeasonalPattern();
        p.setUseTimeRanges(false);
        p.setBaseDuration(baseDuration);
        p.setRanges(new ArrayList<>());
        return p;
    }
}
