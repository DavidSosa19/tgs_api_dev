package com.example.tgs_dev.repository;

import com.example.tgs_dev.entity.RouteOperation;
import com.example.tgs_dev.entity.VehicleAssignment;
import com.example.tgs_dev.repository.base.BaseRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface VehicleAssignmentRepository extends BaseRepository<VehicleAssignment, Integer> {

    /**
     * Returns all active vehicle assignments for a given operation, with
     * {@code vehicle} and {@code scheduleTemplate} eagerly resolved in a
     * single SQL JOIN — eliminating the N+1 lazy-load problem that occurs
     * when serialising the assignment graph.
     *
     * <p>The {@code companyId} parameter is an explicit tenant guard applied
     * at the assignment level (defence-in-depth): even if the caller has
     * already validated that the {@link RouteOperation} belongs to the current
     * tenant, this clause ensures that no cross-tenant assignment can ever
     * be returned.
     *
     * <p>Results are ordered by {@code rowOrder ASC} — the canonical position
     * assigned by the scheduling strategy — so callers receive a deterministic,
     * reproducible sequence without additional sorting.
     *
     * @param operation the parent route operation
     * @param companyId the current tenant's company ID
     * @return assignments ordered by row order, vehicle and template pre-loaded
     */
    @Query("""
            SELECT va
            FROM   VehicleAssignment va
            JOIN   FETCH va.vehicle
            JOIN   FETCH va.scheduleTemplate
            WHERE  va.routeOperation = :operation
            AND    va.company.id = :companyId
            ORDER  BY va.rowOrder ASC
            """)
    List<VehicleAssignment> findByOperationWithDetails(
            @Param("operation") RouteOperation operation,
            @Param("companyId") Integer companyId
    );

    /**
     * Returns <em>all</em> assignments for the given operation — including
     * soft-deleted (inactive) ones — for the audit / original-schedule view.
     *
     * <p>Uses a <strong>native query</strong> so that
     * {@code @SQLRestriction("active = true")} on {@link VehicleAssignment}
     * does not filter out the removed assignments we explicitly want to surface.
     *
     * <p>{@code vehicle} and {@code scheduleTemplate} associations remain
     * lazy — callers must access them within the same transactional scope.
     * For the typical operation size (under 30 vehicles) the resulting
     * lookups are acceptable; the audit endpoint is not performance-critical.
     *
     * @param operationId the parent operation's id
     * @param companyId   the current tenant's company id (explicit tenant guard)
     * @return all assignments ordered by row order, active flag visible on the entity
     */
    @Query(value = """
            SELECT *
            FROM   core.vehicle_assignment
            WHERE  route_operation_id = :operationId
            AND    company_id         = :companyId
            ORDER  BY row_order ASC
            """, nativeQuery = true)
    List<VehicleAssignment> findAllByOperationIncludingInactive(
            @Param("operationId") Integer operationId,
            @Param("companyId")   Integer companyId
    );
}
