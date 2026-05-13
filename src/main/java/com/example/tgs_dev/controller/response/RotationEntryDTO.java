package com.example.tgs_dev.controller.response;

public record RotationEntryDTO(
        Integer id,
        Integer listPosition,
        VehicleDTO vehicle,
        ScheduleTemplateDTO scheduleTemplate
) {}
