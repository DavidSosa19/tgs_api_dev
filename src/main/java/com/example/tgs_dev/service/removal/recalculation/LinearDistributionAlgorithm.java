package com.example.tgs_dev.service.removal.recalculation;

import com.example.tgs_dev.entity.enums.RecalculationScope;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.example.tgs_dev.service.removal.recalculation.StretchedHeadwayShifter.*;

/**
 * Stretches the headway across <em>all</em> remaining vehicles to absorb the
 * gap left by the removed one.
 *
 * <p>Math identical to {@link SubsequentWindowAlgorithm} with
 * {@code windowSize = candidates.size()}: the first candidate takes the
 * removed slot, the last shifts by only {@code gap / N}.  Resulting headway
 * within the operation is {@code H + gap/N}, persisting until the end of the
 * day (no unaffected vehicles remain).
 *
 * <p>Prefer this scope on routes with few vehicles, where spreading the
 * absorption over the whole fleet keeps the per-vehicle delay small.
 *
 * <p>See {@link StretchedHeadwayShifter} for the shared math.
 */
@Component
public class LinearDistributionAlgorithm implements RecalculationAlgorithm {

    @Override
    public RecalculationScope scope() {
        return RecalculationScope.ALL_VEHICLES;
    }

    @Override
    public Map<Integer, Long> computeShifts(RecalculationContext ctx) {
        int n = ctx.candidates().size();
        if (n == 0) return Map.of();

        LocalTime tRemoved = ctx.removedSchedules().getFirst().getDepartureTime();
        LocalTime tNext    = findFirstCandidateBaseTime(ctx, n, tRemoved);
        if (tNext == null) return Map.of();

        long gap = computeGap(tRemoved, tNext);

        Map<Integer, Long> shifts = new LinkedHashMap<>(n);
        for (int i = 0; i < n; i++) {
            long shift = shiftMinutesFor(gap, i, n);
            if (shift != 0L) shifts.put(ctx.candidates().get(i).getId(), shift);
        }
        return Map.copyOf(shifts);
    }
}
