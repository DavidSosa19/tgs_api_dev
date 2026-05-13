package com.example.tgs_dev.controller.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record VehicleRequest(
        @NotBlank String vehicleNumber,
        @NotBlank String licensePlate,
        Integer ownerId
) {}
