package com.example.tgs_dev.controller.response.viewer;

import com.example.tgs_dev.entity.Vehicle;

/**
 * Lean vehicle projection for the schedule viewer.
 *
 * <p>Intentionally excludes {@code owner} (not relevant for schedule display)
 * and {@code active} (the viewer only receives active assignments by design).
 * Adding fields here requires a conscious decision — not an accidental entity dump.
 */
public record VehicleInfoDTO(
        Integer id,
        String  vehicleNumber,
        String  licensePlate
) {
    public static VehicleInfoDTO from(Vehicle vehicle) {
        return new VehicleInfoDTO(
                vehicle.getId(),
                vehicle.getVehicleNumber(),
                vehicle.getLicensePlate()
        );
    }
}
