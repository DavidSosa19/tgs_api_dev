package com.example.tgs_dev.controller.response;

import com.example.tgs_dev.entity.enums.ShiftDayType;

import java.time.LocalDate;

public record VehicleRotationDTO(
        Integer id,
        LocalDate startDate,
        LocalDate endDate,
        Boolean active,
        ShiftDayType rotationType
) {}
