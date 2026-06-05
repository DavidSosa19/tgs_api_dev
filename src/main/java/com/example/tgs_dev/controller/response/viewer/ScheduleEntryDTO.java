package com.example.tgs_dev.controller.response.viewer;

import com.example.tgs_dev.entity.enums.ScheduleOrigin;

import java.time.LocalTime;

/**
 * A single departure entry within a vehicle's daily schedule.
 *
 * <h3>Two ordering dimensions</h3>
 * <ul>
 *   <li>{@code departureOrder} — global slot index in the day across all vehicles
 *       of the route (1..N).  Reflects chronological position of this departure
 *       within the entire day's service.</li>
 *   <li>{@code tripNumber} — per-vehicle trip index (1..K).  Reflects "this is
 *       the n-th round of THIS specific vehicle".  Stable per vehicle even when
 *       slots are inserted or removed from the day.</li>
 * </ul>
 *
 * <h3>Lifecycle fields</h3>
 * <ul>
 *   <li>{@code active} — {@code true} if operationally in effect; {@code false}
 *       if preserved for audit but no longer the plan of record.</li>
 *   <li>{@code origin} — how the row was created
 *       ({@code ORIGINAL} / {@code RECALCULATED} / {@code REPLACEMENT} /
 *       {@code MANUAL}).</li>
 *   <li>{@code originalDepartureTime} — the corresponding {@code ORIGINAL}
 *       row's departure time.  {@code null} for {@code ORIGINAL} rows
 *       themselves (where {@code departureTime} IS the original).</li>
 *   <li>{@code deltaMinutesFromOriginal} — computed by the backend so the
 *       front renders "was X, now Y" without arithmetic.  {@code null} when
 *       the row is the original or no diff applies.</li>
 *   <li>{@code supersededReason} — {@code null} while active; populated when
 *       the row was deactivated ({@code VEHICLE_REMOVED}, {@code RECALCULATED},
 *       {@code REPLACED}, {@code LOANED}, {@code LEGACY_REMOVAL},
 *       {@code MANUAL}).</li>
 * </ul>
 */
public record ScheduleEntryDTO(
        Integer        scheduleId,
        Integer        departureOrder,
        Integer        tripNumber,
        LocalTime      departureTime,
        LocalTime      originalDepartureTime,
        Long           deltaMinutesFromOriginal,
        boolean        active,
        ScheduleOrigin origin,
        String         supersededReason
) {}
