package com.example.tgs_dev.service.removal;

import com.example.tgs_dev.entity.VehicleAssignment;
import com.example.tgs_dev.entity.enums.RecalculationScope;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Carries all user-supplied parameters together with the already-resolved
 * {@link VehicleAssignment} to each {@link VehicleRemovalStrategy}.
 *
 * <p>The orchestrator ({@link com.example.tgs_dev.service.VehicleRemovalService})
 * resolves the assignment once and hands an immutable context to the matching
 * strategy.  All strategies share the same {@code now} timestamp so that
 * audit-log entries within a single removal operation are consistent.
 *
 * <h3>Field requirements by mode</h3>
 * <table border="1">
 *   <tr><th>removalType</th><th>fromTime</th><th>scope</th><th>windowSize</th><th>sourceRouteGroupId</th></tr>
 *   <tr><td>REMOVE_ONLY</td><td>required</td><td>–</td><td>–</td><td>–</td></tr>
 *   <tr><td>REMOVE_RECALCULATE / ALL_VEHICLES</td><td>required</td><td>required</td><td>–</td><td>–</td></tr>
 *   <tr><td>REMOVE_RECALCULATE / SUBSEQUENT_X</td><td>required</td><td>required</td><td>required</td><td>–</td></tr>
 *   <tr><td>REMOVE_REPLACE</td><td>required</td><td>–</td><td>–</td><td>required</td></tr>
 * </table>
 *
 * @param assignment         the assignment being removed (active, not yet soft-deleted)
 * @param now                server-side timestamp captured at removal start; reused throughout
 * @param fromTime           earliest departure to affect (required in all modes)
 * @param recalculationScope which vehicles to affect; {@code null} for non-recalc modes
 * @param windowSize         max vehicles to shift; {@code null} unless scope is {@code SUBSEQUENT_X}
 * @param sourceRouteGroupId stable group ID of the donor route; {@code null} for non-replace modes
 */
public record RemovalContext(
        VehicleAssignment  assignment,
        LocalDateTime      now,
        LocalTime          fromTime,
        RecalculationScope recalculationScope,
        Integer            windowSize,
        Long               sourceRouteGroupId
) {}
