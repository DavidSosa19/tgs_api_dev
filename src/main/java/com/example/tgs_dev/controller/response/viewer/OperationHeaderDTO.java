package com.example.tgs_dev.controller.response.viewer;

import com.example.tgs_dev.entity.RouteOperation;

import java.time.LocalDate;

/**
 * Contextual header for an operation schedule response.
 *
 * <p>Gives the consumer enough information to render a meaningful title
 * ("Route 3 · Monday 15 January 2024") without requiring a second API call.
 *
 * <p>{@code routeId} is exposed so that a frontend component can navigate to
 * the route's detail view directly from the schedule header.
 */
public record OperationHeaderDTO(
        Integer   operationId,
        LocalDate serviceDate,
        Integer   routeId,
        String    routeNumber
) {
    /**
     * Maps a {@link RouteOperation} to its header DTO.
     *
     * <p>Accesses {@code operation.getRoute()} — callers must ensure this is
     * invoked within an active transaction (i.e. inside a {@code @Transactional}
     * service method) to avoid a {@code LazyInitializationException}.
     */
    public static OperationHeaderDTO from(RouteOperation operation) {
        return new OperationHeaderDTO(
                operation.getId(),
                operation.getServiceDate(),
                operation.getRoute().getId(),
                operation.getRoute().getRouteNumber()
        );
    }
}
