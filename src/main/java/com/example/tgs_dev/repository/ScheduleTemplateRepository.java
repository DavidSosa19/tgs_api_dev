package com.example.tgs_dev.repository;

import com.example.tgs_dev.entity.ScheduleTemplate;
import com.example.tgs_dev.repository.base.BaseRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ScheduleTemplateRepository extends BaseRepository<ScheduleTemplate, Integer> {

    /**
     * Fetches all active templates for a company with both route associations
     * eagerly loaded in a single JOIN FETCH query.
     *
     * <p>With OSIV disabled ({@code spring.jpa.open-in-view=false}), accessing
     * the lazy {@code route} or {@code secondaryRoute} proxies after the session
     * closes throws {@code LazyInitializationException}. JOIN FETCHing here
     * keeps both proxies initialized so the mapper can safely call
     * {@code route.getRouteNumber()} outside the session.
     */
    @Query("""
            SELECT DISTINCT t FROM ScheduleTemplate t
            LEFT JOIN FETCH t.route
            LEFT JOIN FETCH t.secondaryRoute
            WHERE t.company.id = :companyId
            """)
    List<ScheduleTemplate> findAllByCompanyWithRoutes(@Param("companyId") Integer companyId);

    /**
     * Single-entity fetch with both routes pre-loaded — prevents
     * {@code LazyInitializationException} on mapper access after session close.
     */
    @Query("""
            SELECT t FROM ScheduleTemplate t
            LEFT JOIN FETCH t.route
            LEFT JOIN FETCH t.secondaryRoute
            WHERE t.id = :id
            AND   t.company.id = :companyId
            """)
    Optional<ScheduleTemplate> findByIdWithRoutes(@Param("id") Integer id,
                                                  @Param("companyId") Integer companyId);
}
