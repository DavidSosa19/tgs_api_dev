package com.example.tgs_dev.repository;

import com.example.tgs_dev.entity.Schedule;
import com.example.tgs_dev.repository.base.BaseRepository;
import com.example.tgs_dev.repository.projection.ScheduleProjection;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ScheduleRepository extends BaseRepository<Schedule, Integer> {

    /**
     * Returns active schedule projections for all given assignment IDs in a
     * single batch query, ordered for deterministic rendering.
     *
     * <p>A {@link ScheduleProjection} is returned instead of full
     * {@link Schedule} entities to avoid loading the {@code company} EAGER
     * association and {@code BaseAudit} fields for every departure row.
     * This keeps the result-set lean — important when a single operation
     * can have O(vehicles × cycleCount) rows.
     *
     * <p>The implicit join on {@code vehicleAssignment} applies
     * {@code @SQLRestriction("active = true")} automatically, so schedules
     * for soft-deleted assignments are excluded.  The explicit
     * {@code s.active = true} filter excludes superseded schedule rows.
     *
     * <p>Primary sort: {@code assignmentId} groups departures by vehicle,
     * matching the outer {@code vehicleSchedules} list order.
     * Secondary sort: {@code tripNumber} guarantees chronological sequence
     * within each vehicle regardless of insertion order.
     *
     * @param assignmentIds IDs of the vehicle assignments to fetch schedules for;
     *                      must not be empty (caller is responsible for the guard)
     * @return active schedule projections ordered by assignment then trip number
     */
    @Query("""
            SELECT s.id                    AS scheduleId,
                   s.vehicleAssignment.id  AS assignmentId,
                   s.departureOrder        AS departureOrder,
                   s.tripNumber            AS tripNumber,
                   s.departureTime         AS departureTime,
                   s.active                AS active,
                   s.origin                AS origin,
                   s.originalDepartureTime AS originalDepartureTime,
                   s.supersededReason      AS supersededReason
            FROM   Schedule s
            WHERE  s.vehicleAssignment.id IN :assignmentIds
            AND    s.active = true
            ORDER  BY s.vehicleAssignment.id ASC,
                      s.tripNumber           ASC
            """)
    List<ScheduleProjection> findScheduleProjectionsByAssignmentIds(
            @Param("assignmentIds") List<Integer> assignmentIds
    );

    /**
     * Returns all schedule projections for the given assignments restricted to
     * {@link com.example.tgs_dev.entity.enums.ScheduleOrigin#ORIGINAL} —
     * regardless of whether the row is currently active.  Used by the audit /
     * "original plan" endpoint to surface the operation as it was at init time.
     *
     * <p>Uses a <strong>native query</strong> so that {@code @SQLRestriction}
     * on {@link com.example.tgs_dev.entity.VehicleAssignment} does not filter
     * out schedules of soft-deleted assignments (the removed vehicle's
     * original plan must remain visible in this view).
     *
     * <p>The query references the underlying column names directly — keep
     * column references in sync with the V22 migration.
     */
    @Query(value = """
            SELECT s.id                    AS scheduleId,
                   s.vehicle_assignment_id AS assignmentId,
                   s.departure_order       AS departureOrder,
                   s.trip_number           AS tripNumber,
                   s.departure_time        AS departureTime,
                   s.active                AS active,
                   s.origin                AS origin,
                   s.original_departure_time AS originalDepartureTime,
                   s.superseded_reason     AS supersededReason
            FROM   core.schedule s
            WHERE  s.vehicle_assignment_id IN :assignmentIds
            AND    s.origin = 'ORIGINAL'
            ORDER  BY s.vehicle_assignment_id ASC,
                      s.trip_number           ASC
            """, nativeQuery = true)
    List<ScheduleProjection> findOriginalProjectionsByAssignmentIds(
            @Param("assignmentIds") List<Integer> assignmentIds
    );
}
