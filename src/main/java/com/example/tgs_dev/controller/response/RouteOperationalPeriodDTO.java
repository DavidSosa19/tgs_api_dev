package com.example.tgs_dev.controller.response;

import com.example.tgs_dev.entity.OperationalPeriodTimeRange;
import com.example.tgs_dev.entity.RouteOperationalPeriod;

import java.time.LocalDate;
import java.util.List;

/**
 * Read model for a {@link RouteOperationalPeriod}.
 *
 * <p>{@code effectiveTo} is {@code null} for open-ended periods; clients should
 * render this as "sin fecha de fin" or equivalent.
 *
 * <p>{@code timeRanges} is always present (empty list when
 * {@code useTimeRanges = false}).
 */
public record RouteOperationalPeriodDTO(
        Integer              id,
        String               label,
        int                  baseDuration,
        int                  cycleCount,
        LocalDate            effectiveFrom,
        LocalDate            effectiveTo,
        boolean              active,
        boolean              useTimeRanges,
        List<RouteTimeRangeDTO> timeRanges
) {
    public static RouteOperationalPeriodDTO from(RouteOperationalPeriod period) {
        List<RouteTimeRangeDTO> ranges = period.getTimeRanges().stream()
                .map(RouteOperationalPeriodDTO::toRangeDTO)
                .toList();

        return new RouteOperationalPeriodDTO(
                period.getId(),
                period.getLabel(),
                period.getBaseDuration(),
                period.getCycleCount(),
                period.getEffectiveFrom(),
                period.getEffectiveTo(),
                Boolean.TRUE.equals(period.getActive()),
                period.isUseTimeRanges(),
                ranges
        );
    }

    private static RouteTimeRangeDTO toRangeDTO(OperationalPeriodTimeRange r) {
        return new RouteTimeRangeDTO(
                r.getId(),
                r.getRangeStart(),
                r.getRangeEnd(),
                r.getDurationMinutes(),
                r.getSortOrder(),
                r.isCrossesMidnight()
        );
    }
}
