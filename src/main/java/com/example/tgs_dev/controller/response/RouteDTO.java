package com.example.tgs_dev.controller.response;

/** Read model for a {@link com.example.tgs_dev.entity.Route}. */
public record RouteDTO(
        Integer id,
        String  routeNumber,
        Boolean active
) {}
