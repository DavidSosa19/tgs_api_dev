package com.example.tgs_dev.controller.response;

import java.time.LocalDate;
import java.util.List;

public record SeasonalPatternDTO(
        Integer                  id,
        String                   name,
        LocalDate                seasonFrom,
        LocalDate                seasonTo,
        Boolean                  useTimeRanges,
        Integer                  baseDuration,
        List<RouteTimeRangeDTO>  ranges
) {}
