package com.example.tgs_dev.repository;

import com.example.tgs_dev.entity.ScheduleTemplate;
import com.example.tgs_dev.repository.base.BaseRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ScheduleTemplateRepository extends BaseRepository<ScheduleTemplate, Integer> {

    /** True if any active template uses this route as primary route. */
    boolean existsByRouteIdAndActiveTrue(Integer routeId);

    /** True if any active template uses this route as secondary route. */
    boolean existsBySecondaryRouteIdAndActiveTrue(Integer routeId);

    // ── Operational / scheduling path (active + current only) ────────────────

    /**
     * Fetches all <em>current active</em> templates for a company with both route
     * associations eagerly loaded (prevents LazyInitializationException, OSIV off).
     */
    @Query("""
            SELECT DISTINCT t FROM ScheduleTemplate t
            LEFT JOIN FETCH t.route
            LEFT JOIN FETCH t.secondaryRoute
            WHERE t.company.id = :companyId
            AND   t.isCurrent  = true
            AND   t.active     = true
            ORDER BY t.templateNumber
            """)
    List<ScheduleTemplate> findAllActiveCurrentByCompanyWithRoutes(@Param("companyId") Integer companyId);

    /**
     * Fetches a single <em>current active</em> template by entity surrogate ID.
     * Used internally by the scheduling engine where FK entity IDs are known.
     */
    @Query("""
            SELECT t FROM ScheduleTemplate t
            LEFT JOIN FETCH t.route
            LEFT JOIN FETCH t.secondaryRoute
            WHERE t.id         = :id
            AND   t.company.id = :companyId
            AND   t.isCurrent  = true
            AND   t.active     = true
            """)
    Optional<ScheduleTemplate> findByIdActiveWithRoutes(@Param("id") Integer id,
                                                        @Param("companyId") Integer companyId);

    // ── SCD navigation queries ────────────────────────────────────────────────

    /** Returns the current active version for the given template group. */
    @Query("""
            SELECT t FROM ScheduleTemplate t
            LEFT JOIN FETCH t.route
            LEFT JOIN FETCH t.secondaryRoute
            WHERE t.group.id   = :groupId
            AND   t.company.id = :companyId
            AND   t.isCurrent  = true
            AND   t.active     = true
            """)
    Optional<ScheduleTemplate> findCurrentActiveByGroupId(@Param("groupId")   Long    groupId,
                                                          @Param("companyId") Integer companyId);

    /** Returns the current version (active or deactivated) for update/reactivate flows. */
    @Query("""
            SELECT t FROM ScheduleTemplate t
            LEFT JOIN FETCH t.route
            LEFT JOIN FETCH t.secondaryRoute
            WHERE t.group.id   = :groupId
            AND   t.company.id = :companyId
            AND   t.isCurrent  = true
            """)
    Optional<ScheduleTemplate> findCurrentByGroupId(@Param("groupId")   Long    groupId,
                                                    @Param("companyId") Integer companyId);

    /**
     * Returns all current versions (active + inactive) for the company listing.
     * Routes are JOIN FETCHed to prevent LazyInitializationException (OSIV off).
     */
    @Query("""
            SELECT t FROM ScheduleTemplate t
            LEFT JOIN FETCH t.route
            LEFT JOIN FETCH t.secondaryRoute
            WHERE t.company.id = :companyId
            AND   t.isCurrent  = true
            ORDER BY CAST(t.templateNumber AS integer)
            """)
    List<ScheduleTemplate> findAllCurrentByCompanyWithRoutes(@Param("companyId") Integer companyId);

    // ── Reactivation (Phase 2) — will be replaced by createNewVersion in Phase 3 ──

    /**
     * Finds a template by entity ID regardless of status; used by the reactivate
     * flow to verify ownership before the UPDATE.
     */
    @Query("""
            SELECT t FROM ScheduleTemplate t
            WHERE t.id = :id
            AND   t.company.id = :companyId
            """)
    Optional<ScheduleTemplate> findByIdAnyStatus(@Param("id") Integer id,
                                                 @Param("companyId") Integer companyId);

    /** Sets {@code active = true} on the given template (Phase-2 reactivation). */
    @Modifying
    @Query("UPDATE ScheduleTemplate t SET t.active = true WHERE t.id = :id")
    void reactivateById(@Param("id") Integer id);
}
