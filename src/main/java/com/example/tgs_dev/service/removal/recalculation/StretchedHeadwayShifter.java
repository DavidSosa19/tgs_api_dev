package com.example.tgs_dev.service.removal.recalculation;

import com.example.tgs_dev.controller.exception.BusinessException;
import com.example.tgs_dev.entity.Schedule;
import com.example.tgs_dev.entity.VehicleAssignment;

import java.time.Duration;
import java.time.LocalTime;
import java.util.List;

/**
 * Shared math for the "stretched headway" redistribution used by both
 * {@link SubsequentWindowAlgorithm} and {@link LinearDistributionAlgorithm}.
 *
 * <h3>Stretched-headway redistribution</h3>
 *
 * <p>When a vehicle is removed, its headway gap ({@code gap = tNext − tRemoved})
 * is absorbed by progressively shifting the next {@code X} candidates earlier.
 * Each candidate {@code i} (0-indexed within the affected window) shifts by:
 *
 * <pre>
 *   shift(i) = gap × (i − X) / X
 * </pre>
 *
 * <p>Results:
 * <ul>
 *   <li>{@code shift(0) = −gap}  →  first candidate takes the removed slot exactly</li>
 *   <li>{@code shift(X−1) = −gap/X}  →  last shifts by the smallest amount</li>
 *   <li>New headway between affected vehicles = original headway + gap/X</li>
 *   <li>Headway between the last affected and the first unaffected naturally
 *       lands at the new stretched value — no boundary discontinuity</li>
 * </ul>
 *
 * <h3>Example</h3>
 * <p>Original headway 5 min, gap 5 min, X = 5:
 * <pre>
 *   V1 (06:00) removed
 *   V2: 06:05 → 06:00 (shift −5)
 *   V3: 06:10 → 06:06 (shift −4)
 *   V4: 06:15 → 06:12 (shift −3)
 *   V5: 06:20 → 06:18 (shift −2)
 *   V6: 06:25 → 06:24 (shift −1)
 *   V7: 06:30 (unaffected) → headway from V6 is 6 — same as the new headway
 * </pre>
 *
 * <h3>Safety</h3>
 * <p>{@link LocalTime#plusMinutes(long)} wraps modularly around midnight.
 * {@link #applyShiftSafely(LocalTime, long)} validates the shift would not
 * cross midnight and throws {@link BusinessException} otherwise.
 */
public final class StretchedHeadwayShifter {

    private StretchedHeadwayShifter() {}

    /**
     * Computes the shift in minutes for candidate at position {@code i} (0-indexed)
     * within a window of size {@code windowSize}, absorbing the given {@code gap}.
     *
     * @return shift in minutes (always &le; 0; {@code -gap} at i=0, smaller magnitudes after)
     */
    public static long shiftMinutesFor(long gap, int i, int windowSize) {
        return (gap * ((long) i - windowSize)) / windowSize;
    }

    /**
     * Computes the absorbed {@code gap} (headway between removed and first
     * qualifying candidate) in minutes.  Always positive; callers may assume
     * {@code tNext > tRemoved} because schedules are filtered by
     * {@code departureTime >= fromTime} and the removed vehicle's qualifying
     * trip precedes the candidates' qualifying trips in the canonical
     * round-robin schedule.
     */
    public static long computeGap(LocalTime tRemoved, LocalTime tNext) {
        return Duration.between(tRemoved, tNext).toMinutes();
    }

    /**
     * Returns the first candidate departure time that belongs to the same
     * cycle as the removed vehicle's first qualifying trip — that is, the
     * earliest qualifying departure of any candidate within the window whose
     * time is {@code >= tRemoved}.
     *
     * <p>The {@code >= tRemoved} guard is critical: a candidate may have
     * earlier qualifying trips (e.g. {@code fromTime = 15:30}, candidate trip
     * 1 at 15:41) that belong to a <em>preceding</em> cycle where the removed
     * vehicle's slot is preserved as history.  Those earlier trips must not
     * be treated as "the candidate's trip that absorbs the removed gap".
     *
     * <p>Returns {@code null} if no candidate in the window has any
     * qualifying schedule {@code >= tRemoved}.
     */
    public static LocalTime findFirstCandidateBaseTime(RecalculationContext ctx,
                                                        int window,
                                                        LocalTime tRemoved) {
        for (int i = 0; i < window; i++) {
            VehicleAssignment va        = ctx.candidates().get(i);
            List<Schedule>    schedules = ctx.qualifyingSchedules().getOrDefault(va.getId(), List.of());
            for (Schedule s : schedules) {
                if (!s.getDepartureTime().isBefore(tRemoved)) {
                    return s.getDepartureTime();
                }
            }
        }
        return null;
    }

    /**
     * Applies the given shift to a {@link LocalTime}, rejecting any shift that
     * would wrap modularly around midnight.  {@link LocalTime#plusMinutes(long)}
     * silently wraps over 24h boundaries, producing structurally valid but
     * operationally wrong results — this guard converts those into a clear
     * {@link BusinessException}.
     *
     * @param current the time to shift
     * @param shiftMins shift in minutes (any sign)
     * @return {@code current + shiftMins}, guaranteed not to have wrapped
     * @throws BusinessException if the shift would cross midnight
     */
    public static LocalTime applyShiftSafely(LocalTime current, long shiftMins) {
        if (shiftMins == 0L) return current;
        LocalTime next = current.plusMinutes(shiftMins);
        if (wrapsAroundMidnight(current, next, shiftMins)) {
            throw new BusinessException("recalculation.shiftWouldWrapMidnight");
        }
        return next;
    }

    /**
     * Detects {@code LocalTime} modular wrap.  Forward shifts wrap when the
     * result lands earlier than the start; backward shifts wrap when the result
     * lands later than the start.
     */
    private static boolean wrapsAroundMidnight(LocalTime current, LocalTime shifted, long shiftMins) {
        if (shiftMins > 0) return shifted.isBefore(current);
        return shifted.isAfter(current);
    }
}
