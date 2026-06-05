package com.example.tgs_dev.controller.response.viewer;

import java.time.LocalDateTime;
import java.util.List;

/**
 * One row in the operation schedule matrix.
 *
 * <p>Maps directly to a single {@code VehicleAssignment}: the assigned vehicle,
 * its schedule template, and the ordered list of departures for the day.
 *
 * <p>{@code vehicleAssignmentId} is included so the frontend can issue mutation
 * requests (vehicle removal, time adjustments) without a separate lookup.
 *
 * <p>Rows are returned ordered by {@code rowOrder} — the position in which the
 * rotation placed this vehicle. The frontend must not re-sort by default.
 *
 * <h3>Active / removed fields</h3>
 * <ul>
 *   <li>{@code active} — {@code true} for vehicles currently performing the
 *       operation; {@code false} for soft-deleted (removed / replaced / loaned)
 *       assignments preserved in the audit view for visual integrity.</li>
 *   <li>{@code removalReason} — {@code null} while active; populated when the
 *       assignment was soft-deleted.  Values:
 *       {@code REMOVED}, {@code REPLACED}, {@code LOANED}.</li>
 *   <li>{@code removedAt} — timestamp of the soft-delete, {@code null} while active.</li>
 * </ul>
 */
public record VehicleScheduleDTO(
        Integer                vehicleAssignmentId,
        Integer                rowOrder,
        boolean                active,
        String                 removalReason,
        LocalDateTime          removedAt,
        VehicleInfoDTO         vehicle,
        TemplateInfoDTO        template,
        List<ScheduleEntryDTO> schedules
) {}
