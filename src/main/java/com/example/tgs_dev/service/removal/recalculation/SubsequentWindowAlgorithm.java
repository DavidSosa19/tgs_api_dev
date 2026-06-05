package com.example.tgs_dev.service.removal.recalculation;

import com.example.tgs_dev.entity.enums.RecalculationScope;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.example.tgs_dev.service.removal.recalculation.StretchedHeadwayShifter.*;

/**
 * Stretches the headway across the next {@code windowSize} vehicles to absorb
 * the gap left by the removed one.
 *
 * <p>Each of the {@code X} candidates inside the window shifts <em>progressively</em>
 * earlier — the first takes the removed slot, the last shifts by only
 * {@code gap / X}.  Resulting headway within the window is {@code H + gap/X};
 * the headway between the last shifted candidate and the first unshifted
 * candidate ends up at the same stretched value, so there is no boundary
 * discontinuity.
 *
 * <p>See {@link StretchedHeadwayShifter} for the shared math and the worked
 * example.
 */
@Component
public class SubsequentWindowAlgorithm implements RecalculationAlgorithm {

    @Override
    public RecalculationScope scope() {
        return RecalculationScope.SUBSEQUENT_X;
    }

    @Override
    public Map<Integer, Long> computeShifts(RecalculationContext ctx) {
        int window = Math.min(ctx.windowSize(), ctx.candidates().size());
        if (window == 0) return Map.of();

        LocalTime tRemoved = ctx.removedSchedules().getFirst().getDepartureTime();
        LocalTime tNext    = findFirstCandidateBaseTime(ctx, window, tRemoved);
        if (tNext == null) return Map.of();

        long gap = computeGap(tRemoved, tNext);

        Map<Integer, Long> shifts = new LinkedHashMap<>(window);
        for (int i = 0; i < window; i++) {
            long shift = shiftMinutesFor(gap, i, window);
            if (shift != 0L) shifts.put(ctx.candidates().get(i).getId(), shift);
        }
        return Map.copyOf(shifts);
    }
}
