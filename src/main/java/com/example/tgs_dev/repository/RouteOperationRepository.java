package com.example.tgs_dev.repository;

import com.example.tgs_dev.entity.RouteOperation;
import com.example.tgs_dev.repository.base.BaseRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface RouteOperationRepository extends BaseRepository<RouteOperation, Integer> {

    /**
     * Returns all active operations for a given date and company, with the
     * {@code route} association eagerly fetched in a single JOIN query.
     *
     * <p>With OSIV disabled ({@code spring.jpa.open-in-view=false}), returning
     * raw entities causes Jackson to hit a {@code LazyInitializationException}
     * when serializing the lazy {@code route} proxy.  Using {@code JOIN FETCH}
     * here ensures the proxy is initialized before the Hibernate session closes,
     * allowing the service to safely map the entities to DTOs.
     *
     * @param date      the operation's service date
     * @param companyId the current tenant's company ID
     * @return operations for that date, route initialized
     */
    @Query("""
            SELECT o FROM RouteOperation o
            JOIN FETCH o.route
            WHERE  o.serviceDate = :date
            AND    o.company.id  = :companyId
            """)
    List<RouteOperation> findAllByDateAndCompany(
            @Param("date")      LocalDate date,
            @Param("companyId") Integer   companyId
    );
}
