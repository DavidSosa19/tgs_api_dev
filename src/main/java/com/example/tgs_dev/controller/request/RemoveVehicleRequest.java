package com.example.tgs_dev.controller.request;

import com.example.tgs_dev.entity.enums.RemovalType;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

/**
 * Request to remove a vehicle from a route operation's schedule.
 *
 * @param vehicleAssignmentId  ID of the VehicleAssignment to remove.
 * @param removalType          How to handle the removal (see {@link RemovalType}).
 * @param effectiveFrom        Only recalculate schedules with departureTime >= this value.
 *                             Required for REMOVE_RECALCULATE; ignored otherwise.
 */
public record RemoveVehicleRequest(
        @NotNull Integer vehicleAssignmentId,
        @NotNull RemovalType removalType,
        LocalTime effectiveFrom
) {}
