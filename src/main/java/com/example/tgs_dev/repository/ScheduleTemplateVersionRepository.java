package com.example.tgs_dev.repository;

import com.example.tgs_dev.entity.ScheduleTemplate;
import com.example.tgs_dev.entity.ScheduleTemplateVersion;
import com.example.tgs_dev.repository.base.BaseRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ScheduleTemplateVersionRepository
        extends BaseRepository<ScheduleTemplateVersion, Integer> {

    /**
     * Returns all active versions for a given template and company,
     * ordered chronologically by start date.
     *
     * @param template  the parent schedule template
     * @param companyId the current tenant's company ID
     * @return versions ordered by effectiveFrom ascending
     */
    @Query("""
            SELECT v FROM ScheduleTemplateVersion v
            WHERE  v.template   = :template
            AND    v.company.id = :companyId
            ORDER  BY v.effectiveFrom ASC
            """)
    List<ScheduleTemplateVersion> findAllByTemplateAndCompany(
            @Param("template")   ScheduleTemplate template,
            @Param("companyId")  Integer          companyId
    );

    /**
     * Returns the single active version whose date window contains {@code date},
     * or {@code empty()} if no version covers that date.
     *
     * <p>Follows the same range-coverage logic as
     * {@link RouteOperationalPeriodRepository#findActiveForDate}: a version
     * covers a date when {@code effectiveFrom <= date} AND
     * ({@code effectiveTo >= date} OR {@code effectiveTo IS NULL}).
     *
     * @param template  the parent schedule template
     * @param companyId the current tenant's company ID
     * @param date      the operation's service date
     * @return the active version for that date, or empty
     */
    @Query("""
            SELECT v FROM ScheduleTemplateVersion v
            WHERE  v.template   = :template
            AND    v.company.id = :companyId
            AND    v.effectiveFrom <= :date
            AND    (v.effectiveTo IS NULL OR v.effectiveTo >= :date)
            ORDER  BY v.effectiveFrom DESC
            """)
    Optional<ScheduleTemplateVersion> findActiveForDate(
            @Param("template")   ScheduleTemplate template,
            @Param("companyId")  Integer          companyId,
            @Param("date")       LocalDate        date
    );

    /**
     * Returns all active versions that overlap with
     * {@code [effectiveFrom, effectiveTo]}, excluding {@code excludeId}.
     *
     * <p>See {@link RouteOperationalPeriodRepository#findOverlapping} for the
     * full overlap semantics.  Pass {@code -1} as {@code excludeId} on create.
     *
     * <p>Uses {@code COALESCE(:effectiveTo, v.effectiveFrom)} instead of
     * {@code :effectiveTo IS NULL OR …} to avoid the
     * {@code could not determine data type of parameter} error that PostgreSQL
     * raises when a nullable parameter appears bare in an {@code IS NULL} predicate
     * with no column providing type-inference context.
     *
     * @param template      the parent schedule template
     * @param companyId     the current tenant's company ID
     * @param effectiveFrom start of the candidate range (inclusive)
     * @param effectiveTo   end of the candidate range (inclusive, nullable)
     * @param excludeId     ID of the version being updated; use {@code -1} on create
     * @return any existing versions that would overlap
     */
    @Query("""
            SELECT v FROM ScheduleTemplateVersion v
            WHERE  v.template   = :template
            AND    v.company.id = :companyId
            AND    v.id        <> :excludeId
            AND    (v.effectiveTo IS NULL OR v.effectiveTo >= :effectiveFrom)
            AND    v.effectiveFrom <= COALESCE(:effectiveTo, v.effectiveFrom)
            """)
    List<ScheduleTemplateVersion> findOverlapping(
            @Param("template")       ScheduleTemplate template,
            @Param("companyId")      Integer          companyId,
            @Param("effectiveFrom")  LocalDate        effectiveFrom,
            @Param("effectiveTo")    LocalDate        effectiveTo,
            @Param("excludeId")      Integer          excludeId
    );
}
