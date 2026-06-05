package com.example.tgs_dev.controller.response;

/**
 * Read model for a {@link com.example.tgs_dev.entity.ScheduleTemplate}.
 *
 * <p>{@code groupId} is the stable business identity used for navigation.
 * {@code id} is the surrogate version ID retained for historical FK references
 * (e.g. {@link com.example.tgs_dev.entity.VehicleAssignment}).
 *
 * <p>{@code sequenceOrder} defines the vehicle's fixed position in the dispatch
 * order for its route (ascending).  Actual departure times are derived from the
 * active {@link com.example.tgs_dev.entity.RouteOperationalPeriod}.
 */
public record ScheduleTemplateDTO(
        Integer  id,
        Long     groupId,
        String   templateNumber,
        String   name,
        Boolean  active,
        Integer  sequenceOrder,
        RouteDTO route,
        RouteDTO secondaryRoute
) {}
