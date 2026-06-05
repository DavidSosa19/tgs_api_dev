package com.example.tgs_dev.repository;

import com.example.tgs_dev.entity.Route;
import com.example.tgs_dev.entity.RouteOperationalPeriod;
import com.example.tgs_dev.repository.base.BaseRepository;
import org.springframework.data.domain.Limit;
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
     * with the {@code timeRanges} collection eagerly initialized.
     *
     * <p>A period covers a date when:
     * <ul>
     *   <li>{@code effectiveFrom <= date}, AND</li>
     *   <li>{@code effectiveTo >= date} OR {@code effectiveTo IS NULL}
     *       (open-ended period).</li>
     * </ul>
     *
     * <p>In a valid dataset there is at most one such period per
     * (route, company, date) tuple — the service layer enforces non-overlap on write,
     * and PostgreSQL's EXCLUDE constraint provides defence-in-depth.  The
     * {@code ORDER BY effectiveFrom DESC} combined with {@link Limit#of(int) Limit.of(1)}
     * is a safety-net that returns the most-recently-started period in the unlikely
     * case of legacy overlap, avoiding {@code NonUniqueResultException}.
     *
     * <p>Uses {@code LEFT JOIN FETCH p.timeRanges} so the collection is hydrated in
     * the same query.  Without it, {@code DepartureSlotGenerator} would dispatch a
     * lazy SELECT on every call, doubling the query count in
     * {@code OperationOrchestratorService.initAllOperations}.
     *
     * <p>Returns {@link List} instead of {@link Optional} to combine ORDER BY with
     * row-limit cleanly; the caller should take {@code stream().findFirst()}.
     *
     * @param route     the parent route
     * @param companyId the current tenant's company ID
     * @param date      the operation's service date
     * @return at most one period (size 0 or 1) with {@code timeRanges} initialized
     */
    @Query("""
            SELECT p FROM RouteOperationalPeriod p
            LEFT   JOIN FETCH p.timeRanges
            WHERE  p.route      = :route
            AND    p.company.id = :companyId
            AND    p.effectiveFrom <= :date
            AND    (p.effectiveTo IS NULL OR p.effectiveTo >= :date)
            ORDER  BY p.effectiveFrom DESC
            """)
    List<RouteOperationalPeriod> findActiveForDate(
            @Param("route")      Route     route,
            @Param("companyId")  Integer   companyId,
            @Param("date")       LocalDate date,
            Limit                          limit
    );

    /**
     * Convenience wrapper that returns the most-recently-started period covering
     * {@code date}, or empty if none exists.  Always applies {@code Limit.of(1)}.
     */
    default Optional<RouteOperationalPeriod> findActiveForDate(Route route,
                                                                Integer companyId,
                                                                LocalDate date) {
        return findActiveForDate(route, companyId, date, Limit.of(1))
                .stream().findFirst();
    }

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
