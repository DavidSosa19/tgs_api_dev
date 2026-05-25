package com.example.tgs_dev.repository;

import com.example.tgs_dev.entity.Schedule;
import com.example.tgs_dev.repository.base.BaseRepository;
import com.example.tgs_dev.repository.projection.ScheduleProjection;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ScheduleRepository extends BaseRepository<Schedule, Integer> {

    /**
     * Returns schedule projections for all given assignment IDs in a single
     * batch query, ordered for deterministic rendering.
     *
     * <p>A {@link ScheduleProjection} is returned instead of full
     * {@link Schedule} entities to avoid loading the {@code company} EAGER
     * association and {@code BaseAudit} fields for every departure row.
     * This keeps the result-set lean — important when a single operation
     * can have O(vehicles × cycleCount) rows.
     *
     * <p>The implicit join on {@code vehicleAssignment} applies
     * {@code @SQLRestriction("active = true")} automatically, so schedules
     * for soft-deleted assignments are excluded without any additional filter.
     *
     * <p>Primary sort: {@code assignmentId} groups departures by vehicle,
     * matching the outer {@code vehicleSchedules} list order.
     * Secondary sort: {@code departureOrder} guarantees chronological sequence
     * within each vehicle regardless of insertion order.
     *
     * @param assignmentIds IDs of the vehicle assignments to fetch schedules for;
     *                      must not be empty (caller is responsible for the guard)
     * @return schedule projections ordered by assignment then departure order
     */
    @Query("""
            SELECT s.vehicleAssignment.id AS assignmentId,
                   s.departureOrder       AS departureOrder,
                   s.departureTime        AS departureTime
            FROM   Schedule s
            WHERE  s.vehicleAssignment.id IN :assignmentIds
            ORDER  BY s.vehicleAssignment.id ASC,
                      s.departureOrder       ASC
            """)
    List<ScheduleProjection> findScheduleProjectionsByAssignmentIds(
            @Param("assignmentIds") List<Integer> assignmentIds
    );

}
