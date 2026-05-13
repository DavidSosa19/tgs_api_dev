package com.example.tgs_dev.controller.response;

public record RouteDTO(
        Integer id,
        String routeNumber,
        Integer baseDuration,
        Integer cycleCount,
        Boolean active
) {}
