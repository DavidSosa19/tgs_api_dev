package com.example.tgs_dev.controller.response.viewer;

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
 */
public record VehicleScheduleDTO(
        Integer                vehicleAssignmentId,
        Integer                rowOrder,
        VehicleInfoDTO         vehicle,
        TemplateInfoDTO        template,
        List<ScheduleEntryDTO> schedules
) {}
