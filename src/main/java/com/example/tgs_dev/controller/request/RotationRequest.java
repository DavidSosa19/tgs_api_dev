package com.example.tgs_dev.controller.request;

import com.example.tgs_dev.entity.enums.ShiftDayType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record RotationRequest(
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        Boolean active,
        @NotNull ShiftDayType rotationType,
        @Valid @NotEmpty List<RotationEntryRequest> entries
) {}
