package com.example.tgs_dev.service.removal.recalculation;

import com.example.tgs_dev.entity.enums.RecalculationScope;

import java.util.Map;

/**
 * Algorithm for computing how to redistribute departure times after a vehicle
 * is removed with {@link com.example.tgs_dev.entity.enums.RemovalType#REMOVE_RECALCULATE}.
 *
 * <p>Each implementation is a Spring {@code @Component} and self-identifies
 * via {@link #scope()}.  {@link com.example.tgs_dev.service.removal.RecalculateHeadwayStrategy}
 * collects all implementations at startup and dispatches to the one matching
 * the operator's chosen {@link RecalculationScope}.
 *
 * <h3>Responsibilities</h3>
 * <p>An algorithm computes <em>shift values</em> only — it does not persist
 * anything.  The strategy applies the shifts by creating new {@link
 * com.example.tgs_dev.entity.Schedule} rows (with origin {@code RECALCULATED})
 * and marking the previous rows inactive.  This separation lets algorithms
 * remain pure math and lets the strategy own the lifecycle bookkeeping.
 *
 * <h3>Implementing a new algorithm</h3>
 * <pre>{@code
 * @Component
 * public class CustomAlgorithm implements RecalculationAlgorithm {
 *
 *     @Override
 *     public RecalculationScope scope() { return RecalculationScope.CUSTOM_SCOPE; }
 *
 *     @Override
 *     public Map<Integer, Long> computeShifts(RecalculationContext ctx) {
 *         // return va_id → shift in minutes
 *     }
 * }
 * }</pre>
 *
 * <h3>Contract</h3>
 * <ul>
 *   <li>{@link #scope()} must return a unique value across all registered beans.</li>
 *   <li>{@link #computeShifts} returns a map keyed by {@code VehicleAssignment.id}.
 *       Candidates whose shift would be zero may be omitted from the map.</li>
 *   <li>Shift values are interpreted as minutes to add to the candidate's
 *       qualifying schedules (negative = move earlier).</li>
 *   <li>Implementations must not mutate the context or its contents.</li>
 * </ul>
 */
public interface RecalculationAlgorithm {

    /**
     * The {@link RecalculationScope} this algorithm handles.
     * Must be unique across all registered algorithm beans.
     */
    RecalculationScope scope();

    /**
     * Computes the shift (in minutes) to apply to each candidate's qualifying
     * schedules.
     *
     * @param ctx pre-filtered, sorted recalculation data
     * @return immutable map: {@code VehicleAssignment.id → shift minutes}
     */
    Map<Integer, Long> computeShifts(RecalculationContext ctx);
}
