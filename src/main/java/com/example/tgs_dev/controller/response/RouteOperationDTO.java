package com.example.tgs_dev.controller.response;

import com.example.tgs_dev.entity.RouteOperation;

import java.time.LocalDate;

/**
 * Read model for a {@link RouteOperation}.
 *
 * <p>Returns only the fields the client needs, avoiding serialization of lazy
 * Hibernate proxies that would throw {@code LazyInitializationException} with
 * OSIV disabled ({@code spring.jpa.open-in-view=false}).
 */
public record RouteOperationDTO(
        Integer   id,
        LocalDate serviceDate,
        RouteRef  route
) {

    /**
     * Minimal route projection — matches the Angular {@code Route} interface
     * ({@code id}, {@code routeNumber}, {@code active}).
     */
    public record RouteRef(Integer id, String routeNumber, boolean active) {}

    /**
     * Maps a {@link RouteOperation} entity to this DTO.
     *
     * <p>Must be called while the Hibernate session is still open (i.e. inside a
     * {@code @Transactional} scope) so that the lazy {@code route} proxy can be
     * initialized.
     */
    public static RouteOperationDTO from(RouteOperation op) {
        return new RouteOperationDTO(
                op.getId(),
                op.getServiceDate(),
                new RouteRef(
                        op.getRoute().getId(),
                        op.getRoute().getRouteNumber(),
                        Boolean.TRUE.equals(op.getRoute().getActive())
                )
        );
    }
}
