package com.example.tgs_dev.controller.response;

/**
 * Read model for a {@link com.example.tgs_dev.entity.Vehicle}.
 *
 * <p>{@code groupId} is the stable business identity used for navigation.
 * {@code id} is the surrogate version ID retained for historical FK references.
 */
public record VehicleDTO(
        Integer   id,
        Long      groupId,
        String    vehicleNumber,
        String    licensePlate,
        Boolean   active,
        PersonDTO owner
) {}
