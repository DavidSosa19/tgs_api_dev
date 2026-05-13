package com.example.tgs_dev.controller.response;

import java.time.LocalTime;

public record ScheduleTemplateDTO(
        Integer id,
        String templateNumber,
        String name,
        Boolean active,
        LocalTime startTime,
        RouteDTO route,
        RouteDTO secondaryRoute
) {}
