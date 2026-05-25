package com.example.tgs_dev.controller.response;

import java.time.LocalDate;
import java.util.List;

public record RouteCalendarOverrideDTO(
        Integer                  id,
        LocalDate                overrideDate,
        Boolean                  useTimeRanges,
        Integer                  baseDuration,
        List<RouteTimeRangeDTO>  ranges
) {}
