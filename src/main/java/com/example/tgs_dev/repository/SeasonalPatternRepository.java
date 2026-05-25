package com.example.tgs_dev.repository;

import com.example.tgs_dev.entity.SeasonalPattern;
import com.example.tgs_dev.repository.base.BaseRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SeasonalPatternRepository extends BaseRepository<SeasonalPattern, Integer> {

    /**
     * Returns all seasonal patterns that overlap with {@code date} for a given route
     * and company, ordered by {@code id} ascending (first-created-wins rule).
     * Fetches ranges eagerly to avoid N+1 during schedule generation.
     */
    @EntityGraph(attributePaths = "ranges")
    @Query("""
            SELECT sp FROM SeasonalPattern sp
            WHERE sp.route.id   = :routeId
              AND sp.company.id = :companyId
              AND sp.seasonFrom <= :date
              AND sp.seasonTo   >= :date
            ORDER BY sp.id ASC
            """)
    List<SeasonalPattern> findActiveForDateOrderById(
            @Param("routeId")   Integer   routeId,
            @Param("companyId") Integer   companyId,
            @Param("date")      LocalDate date);

    /**
     * Convenience method: returns the first (lowest id) active pattern or empty.
     * Uses the same query but limits to the first result in application code.
     */
    default Optional<SeasonalPattern> findFirstActiveForDate(Integer routeId,
                                                             Integer companyId,
                                                             LocalDate date) {
        List<SeasonalPattern> results = findActiveForDateOrderById(routeId, companyId, date);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    /** Returns all patterns for a given route and company (for list/management UI). */
    @Query("""
            SELECT sp FROM SeasonalPattern sp
            WHERE sp.route.id   = :routeId
              AND sp.company.id = :companyId
            ORDER BY sp.seasonFrom ASC
            """)
    List<SeasonalPattern> findAllByRouteAndCompany(
            @Param("routeId")   Integer routeId,
            @Param("companyId") Integer companyId);
}
