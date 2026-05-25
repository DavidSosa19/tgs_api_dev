package com.example.tgs_dev.repository;

import com.example.tgs_dev.entity.Route;
import com.example.tgs_dev.entity.RouteOperationalPeriod;
import com.example.tgs_dev.repository.base.BaseRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RouteOperationalPeriodRepository
        extends BaseRepository<RouteOperationalPeriod, Integer> {

    /**
     * Returns all active periods for a given route and company, ordered
     * chronologically by start date so callers receive a predictable list.
     *
     * <p>The {@code @SQLRestriction("active = true")} on the entity excludes
     * soft-deleted periods automatically.
     *
     * <p>Uses {@code LEFT JOIN FETCH p.timeRanges} so the collection is
     * initialized in the same query, avoiding a {@code LazyInitializationException}
     * when OSIV is disabled ({@code spring.jpa.open-in-view=false}).
     * {@code SELECT DISTINCT} de-duplicates rows produced by the join when a
     * period has multiple time ranges.
     *
     * @param route     the parent route
     * @param companyId the current tenant's company ID
     * @return periods ordered by effectiveFrom ascending, timeRanges initialized
     */
    @Query("""
            SELECT DISTINCT p FROM RouteOperationalPeriod p
            LEFT JOIN FETCH p.timeRanges
            WHERE  p.route     = :route
            AND    p.company.id = :companyId
            ORDER  BY p.effectiveFrom ASC
            """)
    List<RouteOperationalPeriod> findAllByRouteAndCompany(
            @Param("route")      Route   route,
            @Param("companyId")  Integer companyId
    );

    /**
     * Returns the single active period whose date window contains {@code date},
     * or {@code empty()} if no period covers that date.
     *
     * <p>A period covers a date when:
     * <ul>
     *   <li>{@code effectiveFrom <= date}, AND</li>
     *   <li>{@code effectiveTo >= date} OR {@code effectiveTo IS NULL}
     *       (open-ended period).</li>
     * </ul>
     *
     * <p>In a valid dataset there is at most one such period per
     * (route, company, date) tuple — the service layer enforces non-overlap
     * on write.  The {@code ORDER BY effectiveFrom DESC LIMIT 1} is a
     * safety-net that returns the most-recently-started period in the unlikely
     * case of a data integrity issue.
     *
     * @param route     the parent route
     * @param companyId the current tenant's company ID
     * @param date      the operation's service date
     * @return the active period for that date, or empty
     */
    @Query("""
            SELECT p FROM RouteOperationalPeriod p
            WHERE  p.route      = :route
            AND    p.company.id = :companyId
            AND    p.effectiveFrom <= :date
            AND    (p.effectiveTo IS NULL OR p.effectiveTo >= :date)
            ORDER  BY p.effectiveFrom DESC
            """)
    Optional<RouteOperationalPeriod> findActiveForDate(
            @Param("route")      Route     route,
            @Param("companyId")  Integer   companyId,
            @Param("date")       LocalDate date
    );

    /**
     * Returns all active periods that overlap with {@code [effectiveFrom, effectiveTo]},
     * excluding the period identified by {@code excludeId} (pass {@code -1} for
     * create operations where there is no period to exclude yet).
     *
     * <p>Two ranges {@code [A, B]} and {@code [C, D]} overlap when
     * {@code A <= D} (or D is null) AND {@code C <= B} (or B is null).
     * In JPQL this translates to:
     * <ul>
     *   <li>{@code p.effectiveTo IS NULL OR p.effectiveTo >= :effectiveFrom}</li>
     *   <li>{@code :effectiveTo IS NULL OR p.effectiveFrom <= :effectiveTo}</li>
     * </ul>
     *
     * <p>Used by the service layer to enforce the non-overlap invariant before
     * persisting a new or updated period.
     *
     * @param route         the parent route
     * @param companyId     the current tenant's company ID
     * @param effectiveFrom start of the candidate range (inclusive)
     * @param effectiveTo   end of the candidate range (inclusive, nullable)
     * @param excludeId     ID of the period being updated; use {@code -1} on create
     * @return any existing periods that would overlap
     */
    @Query("""
            SELECT p FROM RouteOperationalPeriod p
            WHERE  p.route      = :route
            AND    p.company.id = :companyId
            AND    p.id        <> :excludeId
            AND    (p.effectiveTo IS NULL OR p.effectiveTo >= :effectiveFrom)
            AND    p.effectiveFrom <= COALESCE(:effectiveTo, p.effectiveFrom)
            """)
    List<RouteOperationalPeriod> findOverlapping(
            @Param("route")          Route     route,
            @Param("companyId")      Integer   companyId,
            @Param("effectiveFrom")  LocalDate effectiveFrom,
            @Param("effectiveTo")    LocalDate effectiveTo,
            @Param("excludeId")      Integer   excludeId
    );
}
