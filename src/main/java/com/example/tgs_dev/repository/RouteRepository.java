package com.example.tgs_dev.repository;

import com.example.tgs_dev.entity.Route;
import com.example.tgs_dev.repository.base.BaseRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RouteRepository extends BaseRepository<Route, Integer> {

    /**
     * Returns all <em>current active</em> routes for the company sorted numerically.
     * Used by the operational scheduling path and the route selector dropdowns.
     */
    @Query("""
            SELECT r FROM Route r
            WHERE r.company.id = :companyId
            AND   r.isCurrent  = true
            AND   r.active     = true
            ORDER BY r.routeNumber
            """)
    List<Route> findAllActiveByCompanySorted(@Param("companyId") Integer companyId);

    // ── SCD navigation queries ────────────────────────────────────────────────

    /** Returns the current active version for the given route group. */
    @Query("""
            SELECT r FROM Route r
            WHERE r.group.id   = :groupId
            AND   r.company.id = :companyId
            AND   r.isCurrent  = true
            AND   r.active     = true
            """)
    Optional<Route> findCurrentActiveByGroupId(@Param("groupId")   Long    groupId,
                                               @Param("companyId") Integer companyId);

    /** Returns the current version (active or deactivated) for update/reactivate flows. */
    @Query("""
            SELECT r FROM Route r
            WHERE r.group.id   = :groupId
            AND   r.company.id = :companyId
            AND   r.isCurrent  = true
            """)
    Optional<Route> findCurrentByGroupId(@Param("groupId")   Long    groupId,
                                         @Param("companyId") Integer companyId);

    /**
     * Returns all current versions (active + inactive) for the company listing.
     * Sorted numerically so the UI shows routes in natural order.
     */
    @Query("""
            SELECT r FROM Route r
            WHERE r.company.id = :companyId
            AND   r.isCurrent  = true
            ORDER BY r.routeNumber
            """)
    List<Route> findAllCurrentByCompany(@Param("companyId") Integer companyId);
}
