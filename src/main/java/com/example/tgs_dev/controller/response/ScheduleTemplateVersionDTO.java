package com.example.tgs_dev.controller.response;

import com.example.tgs_dev.entity.ScheduleTemplateVersion;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Read model for a {@link ScheduleTemplateVersion}.
 *
 * <p>{@code label} may be {@code null} when not provided at creation.
 * {@code effectiveTo} is {@code null} for open-ended versions.
 */
public record ScheduleTemplateVersionDTO(
        Integer   id,
        String    label,
        LocalTime startTime,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        boolean   active
) {
    public static ScheduleTemplateVersionDTO from(ScheduleTemplateVersion version) {
        return new ScheduleTemplateVersionDTO(
                version.getId(),
                version.getLabel(),
                version.getStartTime(),
                version.getEffectiveFrom(),
                version.getEffectiveTo(),
                Boolean.TRUE.equals(version.getActive())
        );
    }
}
