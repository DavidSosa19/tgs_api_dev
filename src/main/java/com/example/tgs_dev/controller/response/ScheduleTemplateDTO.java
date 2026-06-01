package com.example.tgs_dev.controller.response;

import java.time.LocalTime;

/**
 * Read model for a {@link com.example.tgs_dev.entity.ScheduleTemplate}.
 *
 * <p>{@code groupId} is the stable business identity used for navigation.
 * {@code id} is the surrogate version ID retained for historical FK references
 * (e.g. {@link com.example.tgs_dev.entity.VehicleAssignment}).
 */
public record ScheduleTemplateDTO(
        Integer   id,
        Long      groupId,
        String    templateNumber,
        String    name,
        Boolean   active,
        LocalTime startTime,
        RouteDTO  route,
        RouteDTO  secondaryRoute
) {}
