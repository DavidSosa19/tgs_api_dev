package com.example.tgs_dev.repository.projection;

import java.time.LocalTime;

/**
 * Spring Data JPA closed projection for schedule batch queries.
 *
 * <p>The alias names in the {@code @Query} on
 * {@code ScheduleRepository.findScheduleProjectionsByAssignmentIds} must match
 * the getter names here (camelCase, without the "get" prefix).
 * Spring Data resolves them by convention at runtime via proxy generation.
 *
 * <p>Using a projection instead of the full {@link com.example.tgs_dev.entity.Schedule}
 * entity avoids loading the {@code company} EAGER association and all
 * {@code BaseAudit} fields for every departure row — reducing result-set
 * size and object-graph overhead significantly.
 *
 * <h3>Lifecycle fields</h3>
 * The projection now carries the soft-delete + lineage fields added in V22 so
 * the matrix DTO can render diffs ("was X, now Y"), greyed-out historical rows
 * and superseded reasons without follow-up queries.
 *
 * <h3>Enum field representation</h3>
 * {@link #getOrigin()} returns a {@code String} (the enum's name) rather than
 * the {@code ScheduleOrigin} enum directly so the same projection works for
 * both JPQL queries (where Spring converts via {@code toString()}) and native
 * queries (where the column is a raw VARCHAR).
 *
 * <h3>Test support</h3>
 * <p>Test instances can be created with
 * {@link com.example.tgs_dev.TestFixtures#scheduleProjection}.
 */
public interface ScheduleProjection {

    Integer   getScheduleId();
    Integer   getAssignmentId();
    Integer   getDepartureOrder();
    Integer   getTripNumber();
    LocalTime getDepartureTime();

    Boolean   getActive();
    String    getOrigin();
    LocalTime getOriginalDepartureTime();
    String    getSupersededReason();
}
