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
 * Unit tests for {@link FixedDurationResolver}.
 *
 * <p>This is the terminal link of the {@link DurationResolver} chain and always
 * returns {@code effectiveBaseDuration} from the context.
 */
@DisplayName("FixedDurationResolver")
class FixedDurationResolverTest {

    private final FixedDurationResolver sut = new FixedDurationResolver();

    @Test @DisplayName("returns effectiveBaseDuration from context")
    void returnsBaseDuration() {
        Route route = route(1, "R-1");
        DurationResolverContext ctx = new DurationResolverContext(
                route, LocalTime.of(8, 0), LocalDate.of(2024, 6, 15), 45, List.of());

        assertThat(sut.resolve(ctx)).isEqualTo(45);
    }

    @Test @DisplayName("returns period-overridden effectiveBaseDuration when one is supplied")
    void returnsPeriodBaseDuration() {
        Route route = route(1, "R-1");
        DurationResolverContext ctx = new DurationResolverContext(
                route, LocalTime.of(8, 0), LocalDate.of(2024, 12, 15), 60, List.of());

        assertThat(sut.resolve(ctx)).isEqualTo(60);
    }

    @Test @DisplayName("returns the exact effectiveBaseDuration value — no rounding or offset")
    void returnsExactValue() {
        Route route = route(2, "R-2");
        DurationResolverContext ctx = new DurationResolverContext(
                route, LocalTime.MIDNIGHT, LocalDate.now(), 120, List.of());

        assertThat(sut.resolve(ctx)).isEqualTo(120);
    }
}
