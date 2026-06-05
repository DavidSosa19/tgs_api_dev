package com.example.tgs_dev.controller.response.viewer;

import com.example.tgs_dev.entity.ScheduleTemplate;

/**
 * Lean schedule-template projection for the schedule viewer.
 *
 * <p>Excludes route associations — the operation header already carries route
 * context.  Carries {@code sequenceOrder} (the template's dispatch position)
 * instead of an explicit time — actual departure times are derived from the
 * active {@link com.example.tgs_dev.entity.RouteOperationalPeriod}.
 */
public record TemplateInfoDTO(
        Integer id,
        String  templateNumber,
        String  name,
        Integer sequenceOrder
) {
    public static TemplateInfoDTO from(ScheduleTemplate template) {
        return new TemplateInfoDTO(
                template.getId(),
                template.getTemplateNumber(),
                template.getName(),
                template.getSequenceOrder()
        );
    }
}
