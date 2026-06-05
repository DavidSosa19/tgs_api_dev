package com.example.tgs_dev.repository;

import com.example.tgs_dev.entity.RouteOperation;
import com.example.tgs_dev.repository.base.BaseRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RouteOperationRepository extends BaseRepository<RouteOperation, Integer> {

    /** True if any active operation exists for the given route. */
    boolean existsByRouteIdAndActiveTrue(Integer routeId);

    /**
     * Returns the IDs of routes (surrogate ids) that already have an active
     * operation for the given {@code date} within the given company.
     *
     * <p>Used by {@code OperationOrchestratorService.initAllOperations} to skip
     * routes that have already been initialised — enforcing idempotency at the
     * service layer.  A single batch query is preferred over per-route existence
     * checks (N+1).
     *
     * @param date      the operation's service date
     * @param companyId the current tenant's company ID
     * @return the surrogate route ids that already have an active operation
     */
    @Query("""
            SELECT o.route.id FROM RouteOperation o
            WHERE  o.serviceDate = :date
            AND    o.company.id  = :companyId
            """)
    List<Integer> findRouteIdsWithActiveOperation(
            @Param("date")      LocalDate date,
            @Param("companyId") Integer   companyId
    );

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
     * <h3>Ordering</h3>
     * Results are sorted naturally by {@code routeNumber}.  Since {@code routeNumber}
     * is a {@code String}, a plain {@code ORDER BY} would sort lexicographically
     * ({@code "1", "10", "11", "2"}).  Sorting first by {@code LENGTH}
     * and then by the string itself produces correct numeric order for purely
     * numeric route numbers ({@code "1", "2", "10", "11"}) and degrades gracefully
     * to grouped lexicographic order for mixed-alphanumeric values.
     *
     * @param date      the operation's service date
     * @param companyId the current tenant's company ID
     * @return operations for that date, route initialized, ordered by route number
     */
    @Query("""
            SELECT o FROM RouteOperation o
            JOIN   FETCH o.route r
            WHERE  o.serviceDate = :date
            AND    o.company.id  = :companyId
            ORDER  BY LENGTH(r.routeNumber) ASC, r.routeNumber ASC
            """)
    List<RouteOperation> findAllByDateAndCompany(
            @Param("date")      LocalDate date,
            @Param("companyId") Integer   companyId
    );

    /**
     * Loads the operation with a {@code PESSIMISTIC_WRITE} row lock so that
     * concurrent removal / recalculation transactions on the same operation
     * serialise instead of racing.  Combined with the {@code @Version} column,
     * this provides defence-in-depth against lost-update bugs.
     *
     * <p>Must be called within an active {@code @Transactional} context.  The
     * lock is held for the duration of the transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT op FROM RouteOperation op WHERE op.id = :id")
    Optional<RouteOperation> findByIdForUpdate(@Param("id") Integer id);
}
