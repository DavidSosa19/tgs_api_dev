package com.example.tgs_dev.controller.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for creating or updating a vehicle.
 *
 * <p>{@code ownerId} is the {@link com.example.tgs_dev.entity.PersonGroup} id
 * (stable business identity), not the surrogate version id.
 */
public record VehicleRequest(
        @NotBlank String vehicleNumber,
        @NotBlank String licensePlate,
        Long ownerId
) {}
