package com.example.tgs_dev.controller.response.viewer;

import com.example.tgs_dev.entity.ScheduleTemplate;

import java.time.LocalTime;

/**
 * Lean schedule-template projection for the schedule viewer.
 *
 * <p>Excludes route associations — the operation header already carries route
 * context.  The frontend needs template identification and its start time to
 * render the matrix columns correctly.
 */
public record TemplateInfoDTO(
        Integer   id,
        String    templateNumber,
        String    name,
        LocalTime startTime
) {
    public static TemplateInfoDTO from(ScheduleTemplate template) {
        return new TemplateInfoDTO(
                template.getId(),
                template.getTemplateNumber(),
                template.getName(),
                template.getStartTime()
        );
    }
}
