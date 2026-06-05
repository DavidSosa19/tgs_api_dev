package com.example.tgs_dev.service.schedule;

import com.example.tgs_dev.entity.Route;
import com.example.tgs_dev.entity.SeasonalPattern;
import com.example.tgs_dev.entity.SeasonalPatternRange;
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

import static com.example.tgs_dev.TestFixtures.route;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SeasonalDurationResolver")
class SeasonalDurationResolverTest {

    @Mock SeasonalPatternService seasonalPatternService;
    @Mock DurationResolver       next;

    SeasonalDurationResolver sut;

    private static final LocalDate DATE  = LocalDate.of(2024, 7, 15); // mid-summer
    private static final Route     ROUTE = route(1, "R1");

    @BeforeEach
    void setUp() {
        sut = new SeasonalDurationResolver(seasonalPatternService, next);
    }

    // ── No seasonal pattern ────────────────────────────────────────────────────

    @Test @DisplayName("delegates to next when no seasonal pattern covers the date")
    void noPattern_delegatesToNext() {
        when(seasonalPatternService.findActivePatternForDate(ROUTE, DATE)).thenReturn(Optional.empty());
        when(next.resolve(any())).thenReturn(30);

        assertThat(sut.resolve(ctx(LocalTime.of(8, 0)))).isEqualTo(30);
        verify(next).resolve(any());
    }

    // ── Fixed seasonal pattern ─────────────────────────────────────────────────

    @Nested @DisplayName("pattern with useTimeRanges = false (fixed)")
    class FixedPattern {
        @Test @DisplayName("returns pattern baseDuration for any departure time")
        void returnsPatternBaseDuration() {
            when(seasonalPatternService.findActivePatternForDate(ROUTE, DATE))
                    .thenReturn(Optional.of(fixedPattern(45)));

            assertThat(sut.resolve(ctx(LocalTime.of(6, 0)))).isEqualTo(45);
            assertThat(sut.resolve(ctx(LocalTime.of(20, 0)))).isEqualTo(45);
            verifyNoInteractions(next);
        }
    }

    // ── Range-based seasonal pattern ──────────────────────────────────────────

    @Nested @DisplayName("pattern with useTimeRanges = true")
    class RangePattern {
        @Test @DisplayName("returns matched range duration when departure is inside a range")
        void matchedRange() {
            SeasonalPattern pattern = rangePattern(50,
                    patternRange(LocalTime.of(6, 0), LocalTime.of(9, 0),  90),
                    patternRange(LocalTime.of(9, 0), LocalTime.of(18, 0), 45));
            when(seasonalPatternService.findActivePatternForDate(ROUTE, DATE))
                    .thenReturn(Optional.of(pattern));

            assertThat(sut.resolve(ctx(LocalTime.of(7, 0)))).isEqualTo(90);
            assertThat(sut.resolve(ctx(LocalTime.of(11, 0)))).isEqualTo(45);
            verifyNoInteractions(next);
        }

        @Test @DisplayName("falls back to pattern baseDuration for gaps — NOT to next/route default")
        void gapUsesPatternBaseDuration() {
            SeasonalPattern pattern = rangePattern(50,
                    patternRange(LocalTime.of(6, 0),  LocalTime.of(9, 0),  90),
                    patternRange(LocalTime.of(10, 0), LocalTime.of(18, 0), 45));
            when(seasonalPatternService.findActivePatternForDate(ROUTE, DATE))
                    .thenReturn(Optional.of(pattern));

            // 09:30 is in the gap → pattern.baseDuration (50), next is not called
            assertThat(sut.resolve(ctx(LocalTime.of(9, 30)))).isEqualTo(50);
            verifyNoInteractions(next);
        }
    }

    // ── Priority verification ─────────────────────────────────────────────────

    @Test @DisplayName("seasonal pattern takes priority — next is not consulted when pattern exists")
    void seasonalTakesPriority() {
        when(seasonalPatternService.findActivePatternForDate(ROUTE, DATE))
                .thenReturn(Optional.of(fixedPattern(99)));

        sut.resolve(ctx(LocalTime.of(8, 0)));

        verifyNoInteractions(next); // next (CalendarOverrideDurationResolver) is NOT called
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ScheduleResolverContext ctx(LocalTime time) {
        return new ScheduleResolverContext(ROUTE, time, DATE, 30, 8, List.of());
    }

    private SeasonalPattern fixedPattern(int baseDuration) {
        SeasonalPattern p = new SeasonalPattern();
        p.setUseTimeRanges(false);
        p.setBaseDuration(baseDuration);
        p.setRanges(new ArrayList<>());
        return p;
    }

    private SeasonalPattern rangePattern(int baseDuration, SeasonalPatternRange... ranges) {
        SeasonalPattern p = new SeasonalPattern();
        p.setUseTimeRanges(true);
        p.setBaseDuration(baseDuration);
        p.setRanges(new ArrayList<>(List.of(ranges)));
        return p;
    }

    private SeasonalPatternRange patternRange(LocalTime start, LocalTime end, int duration) {
        return new SeasonalPatternRange(start, end, duration, 0, false);
    }
}
