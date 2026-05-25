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
 * <h3>Test support</h3>
 * <p>Test instances can be created with
 * {@link com.example.tgs_dev.TestFixtures#scheduleProjection}.
 */
public interface ScheduleProjection {

    Integer   getAssignmentId();
    Integer   getDepartureOrder();
    LocalTime getDepartureTime();
}
