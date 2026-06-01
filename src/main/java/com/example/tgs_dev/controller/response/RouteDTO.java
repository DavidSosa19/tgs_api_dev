package com.example.tgs_dev.controller.response;

/**
 * Read model for a {@link com.example.tgs_dev.entity.Route}.
 *
 * <p>{@code groupId} is the stable business identity used for navigation.
 * {@code id} is the surrogate version ID retained for historical FK references.
 */
public record RouteDTO(
        Integer id,
        Long    groupId,
        String  routeNumber,
        Boolean active
) {}
