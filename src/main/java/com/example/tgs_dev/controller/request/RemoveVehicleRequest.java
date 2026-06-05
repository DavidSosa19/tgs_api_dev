package com.example.tgs_dev.controller.request;

import com.example.tgs_dev.entity.enums.RecalculationScope;
import com.example.tgs_dev.entity.enums.RemovalType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalTime;

/**
 * Request payload for {@code POST /api/route-operations/vehicle/remove}.
 *
 * <h3>Field requirements by mode</h3>
 * <table border="1">
 *   <tr>
 *     <th>removalType</th>
 *     <th>fromTime</th>
 *     <th>recalculationScope</th>
 *     <th>windowSize</th>
 *     <th>sourceRouteGroupId</th>
 *   </tr>
 *   <tr><td>REMOVE_ONLY</td><td>required</td><td>–</td><td>–</td><td>–</td></tr>
 *   <tr><td>REMOVE_RECALCULATE / ALL_VEHICLES</td><td>required</td><td>required</td><td>–</td><td>–</td></tr>
 *   <tr><td>REMOVE_RECALCULATE / SUBSEQUENT_X</td><td>required</td><td>required</td><td>required</td><td>–</td></tr>
 *   <tr><td>REMOVE_REPLACE</td><td>required</td><td>–</td><td>–</td><td>required</td></tr>
 * </table>
 *
 * <p>{@code fromTime} is the boundary that divides "historical" from "new" in
 * every mode:
 * <ul>
 *   <li>Schedules with {@code departureTime < fromTime} are preserved as
 *       historical record (they happened or were planned before the change).</li>
 *   <li>Schedules with {@code departureTime >= fromTime} are marked inactive
 *       and, depending on the mode, replaced by new schedule rows.</li>
 * </ul>
 *
 * <p>Cross-field validation (e.g. requiring {@code windowSize} when scope is
 * {@code SUBSEQUENT_X}) is enforced by the strategy via
 * {@link com.example.tgs_dev.controller.exception.BusinessException} because
 * the constraint applies conditionally on {@code removalType}.  Field-level
 * validation ({@code @Positive}, {@code @NotNull}) is enforced by Bean
 * Validation here.
 *
 * @param vehicleAssignmentId  ID of the assignment to remove (positive)
 * @param removalType          how the removal is handled
 * @param fromTime             boundary between historical and new (required, all modes)
 * @param recalculationScope   which vehicles to recalculate (REMOVE_RECALCULATE only)
 * @param windowSize           number of subsequent vehicles to shift (SUBSEQUENT_X only; positive)
 * @param sourceRouteGroupId   stable group ID of the donor route (REMOVE_REPLACE only; positive)
 */
public record RemoveVehicleRequest(
        @NotNull @Positive Integer            vehicleAssignmentId,
        @NotNull           RemovalType        removalType,
        @NotNull           LocalTime          fromTime,
                           RecalculationScope recalculationScope,
        @Positive          Integer            windowSize,
        @Positive          Long               sourceRouteGroupId
) {}
