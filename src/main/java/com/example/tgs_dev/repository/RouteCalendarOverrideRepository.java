package com.example.tgs_dev.repository;

import com.example.tgs_dev.entity.RouteCalendarOverride;
import com.example.tgs_dev.repository.base.BaseRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface RouteCalendarOverrideRepository extends BaseRepository<RouteCalendarOverride, Integer> {

    /**
     * Finds the override for a specific (route, date, company) triple.
     * Fetches ranges eagerly to avoid N+1 during schedule generation.
     */
    @EntityGraph(attributePaths = "ranges")
    @Query("""
            SELECT o FROM RouteCalendarOverride o
            WHERE o.route.id    = :routeId
              AND o.overrideDate = :date
              AND o.company.id  = :companyId
            """)
    Optional<RouteCalendarOverride> findByRouteAndDateAndCompany(
            @Param("routeId")    Integer   routeId,
            @Param("date")       LocalDate date,
            @Param("companyId")  Integer   companyId);
}
