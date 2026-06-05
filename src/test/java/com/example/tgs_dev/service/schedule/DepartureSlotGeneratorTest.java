package com.example.tgs_dev.service.schedule;

import com.example.tgs_dev.controller.exception.BusinessException;
import com.example.tgs_dev.entity.Route;
import com.example.tgs_dev.entity.RouteOperationalPeriod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static com.example.tgs_dev.TestFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DepartureSlotGenerator}.
 *
 * <p>The {@link HeadwayResolver} is mocked so headway values are controlled
 * independently from resolver-chain behaviour (tested in resolver unit tests).
 */
@DisplayName("DepartureSlotGenerator")
class DepartureSlotGeneratorTest {

    private final HeadwayResolver        resolver = mock(HeadwayResolver.class);
    private final DepartureSlotGenerator sut      = new DepartureSlotGenerator(resolver);

    private static final Route     ROUTE = route(1, "R1");
    private static final LocalDate DATE  = LocalDate.of(2024, 6, 15);

    /** Standard period: 06:00 – window end, 10-min fixed headway. */
    private RouteOperationalPeriod period(LocalTime first, LocalTime last) {
        return operationalPeriod(1, ROUTE, 90, 10, LocalDate.of(2024, 1, 1), null,
                                 first, last);
    }

    // ── Validation guard ──────────────────────────────────────────────────────

    @Nested @DisplayName("period config validation")
    class Validation {

        @Test @DisplayName("throws when firstDeparture is null")
        void nullFirstDeparture() {
            RouteOperationalPeriod p = nullDeparturePeriod(true);
            assertThatThrownBy(() -> sut.generate(p, ROUTE, DATE))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("missingDepartureTimes");
        }

        @Test @DisplayName("throws when lastDeparture is null")
        void nullLastDeparture() {
            RouteOperationalPeriod p = nullDeparturePeriod(false);
            assertThatThrownBy(() -> sut.generate(p, ROUTE, DATE))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("missingDepartureTimes");
        }

        @Test @DisplayName("throws when defaultHeadwayMinutes is null")
        void nullHeadway() {
            RouteOperationalPeriod p = operationalPeriod(1, ROUTE, 90, 10,
                    LocalDate.of(2024, 1, 1), null,
                    LocalTime.of(6, 0), LocalTime.of(22, 0));
            p.setDefaultHeadwayMinutes(null);
            assertThatThrownBy(() -> sut.generate(p, ROUTE, DATE))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("missingDefaultHeadway");
        }

        @Test @DisplayName("throws when defaultHeadwayMinutes is 0")
        void zeroHeadway() {
            RouteOperationalPeriod p = operationalPeriod(1, ROUTE, 90, 0,
                    LocalDate.of(2024, 1, 1), null,
                    LocalTime.of(6, 0), LocalTime.of(22, 0));
            assertThatThrownBy(() -> sut.generate(p, ROUTE, DATE))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("missingDefaultHeadway");
        }

        @Test @DisplayName("throws when firstDeparture is not before lastDeparture")
        void invalidDepartureRange() {
            RouteOperationalPeriod p = period(LocalTime.of(22, 0), LocalTime.of(6, 0));
            assertThatThrownBy(() -> sut.generate(p, ROUTE, DATE))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("invalidDepartureRange");
        }

        @Test @DisplayName("throws when firstDeparture equals lastDeparture")
        void equalDepartures() {
            RouteOperationalPeriod p = period(LocalTime.of(8, 0), LocalTime.of(8, 0));
            assertThatThrownBy(() -> sut.generate(p, ROUTE, DATE))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("invalidDepartureRange");
        }
    }

    // ── Slot generation ───────────────────────────────────────────────────────

    @Nested @DisplayName("slot generation")
    class SlotGeneration {

        @Test @DisplayName("single slot when window is smaller than one headway")
        void singleSlot() {
            when(resolver.resolve(any())).thenReturn(60);
            RouteOperationalPeriod p = period(LocalTime.of(8, 0), LocalTime.of(8, 30));

            assertThat(sut.generate(p, ROUTE, DATE))
                    .containsExactly(LocalTime.of(8, 0));
        }

        @Test @DisplayName("exactly 3 slots for 2-hour window at 60-min headway")
        void threeSlotsFixedHeadway() {
            when(resolver.resolve(any())).thenReturn(60);
            RouteOperationalPeriod p = period(LocalTime.of(6, 0), LocalTime.of(8, 0));

            assertThat(sut.generate(p, ROUTE, DATE)).containsExactly(
                    LocalTime.of(6, 0),
                    LocalTime.of(7, 0),
                    LocalTime.of(8, 0));
        }

        @Test @DisplayName("lastDeparture slot is included when it aligns exactly")
        void lastDepartureIncluded() {
            when(resolver.resolve(any())).thenReturn(30);
            RouteOperationalPeriod p = period(LocalTime.of(6, 0), LocalTime.of(7, 0));

            List<LocalTime> slots = sut.generate(p, ROUTE, DATE);

            assertThat(slots).last().isEqualTo(LocalTime.of(7, 0));
        }

        @Test @DisplayName("result list is immutable")
        void resultIsImmutable() {
            when(resolver.resolve(any())).thenReturn(60);
            RouteOperationalPeriod p = period(LocalTime.of(6, 0), LocalTime.of(7, 0));

            List<LocalTime> slots = sut.generate(p, ROUTE, DATE);

            assertThatThrownBy(() -> slots.add(LocalTime.MIDNIGHT))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test @DisplayName("all slots are strictly increasing")
        void slotsAreStrictlyIncreasing() {
            when(resolver.resolve(any())).thenReturn(10);
            RouteOperationalPeriod p = period(LocalTime.of(6, 0), LocalTime.of(8, 0));

            List<LocalTime> slots = sut.generate(p, ROUTE, DATE);

            for (int i = 1; i < slots.size(); i++) {
                assertThat(slots.get(i)).isAfter(slots.get(i - 1));
            }
        }

        @Test @DisplayName("variable headway — resolver is called with the current departure time")
        void variableHeadway() {
            // 06:00 → +30 → 06:30 → +15 → 06:45 → +30 → 07:15 (exceeds 07:00, stop)
            when(resolver.resolve(any()))
                    .thenReturn(30).thenReturn(15).thenReturn(30);
            RouteOperationalPeriod p = period(LocalTime.of(6, 0), LocalTime.of(7, 0));

            assertThat(sut.generate(p, ROUTE, DATE)).containsExactly(
                    LocalTime.of(6, 0),
                    LocalTime.of(6, 30),
                    LocalTime.of(6, 45));
        }
    }

    // ── Resolver contract enforcement ─────────────────────────────────────────

    @Nested @DisplayName("resolver contract enforcement")
    class ResolverContract {

        @Test @DisplayName("throws IllegalStateException when resolver returns 0")
        void resolverReturnsZero() {
            when(resolver.resolve(any())).thenReturn(0);
            RouteOperationalPeriod p = period(LocalTime.of(6, 0), LocalTime.of(22, 0));

            assertThatThrownBy(() -> sut.generate(p, ROUTE, DATE))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("non-positive");
        }

        @Test @DisplayName("throws IllegalStateException when resolver returns negative")
        void resolverReturnsNegative() {
            when(resolver.resolve(any())).thenReturn(-5);
            RouteOperationalPeriod p = period(LocalTime.of(6, 0), LocalTime.of(22, 0));

            assertThatThrownBy(() -> sut.generate(p, ROUTE, DATE))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("non-positive");
        }
    }

    // ── Resolver receives correct context ─────────────────────────────────────

    @Nested @DisplayName("context passed to resolver")
    class ContextPropagation {

        @Test @DisplayName("context carries the current departure time on each call")
        void contextCarriesDepartureTime() {
            when(resolver.resolve(any())).thenReturn(30);
            RouteOperationalPeriod p = period(LocalTime.of(6, 0), LocalTime.of(7, 0));

            sut.generate(p, ROUTE, DATE);

            // First call should carry 06:00, second 06:30, third 07:00
            verify(resolver, times(3)).resolve(any());
            verify(resolver).resolve(argThat(ctx ->
                    ctx.departureTime().equals(LocalTime.of(6, 0))));
            verify(resolver).resolve(argThat(ctx ->
                    ctx.departureTime().equals(LocalTime.of(6, 30))));
            verify(resolver).resolve(argThat(ctx ->
                    ctx.departureTime().equals(LocalTime.of(7, 0))));
        }

        @Test @DisplayName("empty effectiveTimeRanges when useTimeRanges is false")
        void emptyRangesWhenDisabled() {
            when(resolver.resolve(any())).thenReturn(30);
            RouteOperationalPeriod p = period(LocalTime.of(6, 0), LocalTime.of(6, 31));
            // useTimeRanges defaults to false

            sut.generate(p, ROUTE, DATE);

            verify(resolver, atLeastOnce()).resolve(
                    argThat(ctx -> ctx.effectiveTimeRanges().isEmpty()));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Creates a period with one null departure time for validation tests. */
    private RouteOperationalPeriod nullDeparturePeriod(boolean nullifyFirst) {
        RouteOperationalPeriod p = operationalPeriod(1, ROUTE, 90, 10,
                LocalDate.of(2024, 1, 1), null,
                LocalTime.of(6, 0), LocalTime.of(22, 0));
        if (nullifyFirst) p.setFirstDeparture(null);
        else              p.setLastDeparture(null);
        return p;
    }
}
