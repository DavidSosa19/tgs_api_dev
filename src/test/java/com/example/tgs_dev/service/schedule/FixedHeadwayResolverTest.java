package com.example.tgs_dev.service.schedule;

import com.example.tgs_dev.entity.Route;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static com.example.tgs_dev.TestFixtures.route;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link FixedHeadwayResolver}.
 *
 * <p>The resolver is a leaf node — it always returns
 * {@code context.effectiveDefaultHeadway()}, regardless of time or ranges.
 */
@DisplayName("FixedHeadwayResolver")
class FixedHeadwayResolverTest {

    private final FixedHeadwayResolver sut = new FixedHeadwayResolver();

    private static final Route     ROUTE = route(1, "R1");
    private static final LocalDate DATE  = LocalDate.of(2024, 6, 15);

    private ScheduleResolverContext ctx(int defaultHeadway) {
        return new ScheduleResolverContext(
                ROUTE, LocalTime.of(8, 0), DATE,
                90, defaultHeadway, List.of());
    }

    @Test @DisplayName("returns the effectiveDefaultHeadway from context")
    void returnsDefaultHeadway() {
        assertThat(sut.resolve(ctx(12))).isEqualTo(12);
    }

    @Test @DisplayName("returns different headway values correctly")
    void returnsOtherHeadwayValues() {
        assertThat(sut.resolve(ctx(5))).isEqualTo(5);
        assertThat(sut.resolve(ctx(60))).isEqualTo(60);
    }

    @Test @DisplayName("ignores effectiveTimeRanges — always uses default headway")
    void ignoresTimeRanges() {
        ScheduleResolverContext ctxWithRanges = new ScheduleResolverContext(
                ROUTE, LocalTime.of(8, 0), DATE,
                90, 15,
                List.of(new TimeRangeLookup(
                        LocalTime.of(6, 0), LocalTime.of(20, 0), 60, 8, false)));

        assertThat(sut.resolve(ctxWithRanges)).isEqualTo(15);
    }
}
