package com.example.tgs_dev.controller.response;

import java.time.LocalTime;

public record RouteTimeRangeDTO(
        Integer   id,
        LocalTime rangeStart,
        LocalTime rangeEnd,
        int       durationMinutes,
        int       sortOrder,
        boolean   crossesMidnight
) {}
