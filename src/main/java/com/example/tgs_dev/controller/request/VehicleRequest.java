package com.example.tgs_dev.controller.request;

import jakarta.validation.constraints.NotBlank;

public record VehicleRequest(
        @NotBlank String vehicleNumber,
        @NotBlank String licensePlate,
        Integer ownerId
) {}
