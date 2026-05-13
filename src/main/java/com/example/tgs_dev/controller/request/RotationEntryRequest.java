package com.example.tgs_dev.controller.request;

import jakarta.validation.constraints.NotNull;

public record RotationEntryRequest(
        @NotNull Integer vehicleId,
        @NotNull Integer scheduleTemplateId,
        int listPosition
) {}
