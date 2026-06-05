package com.example.tgs_dev.entity.enums;

/**
 * Controls which vehicle assignments are affected when a vehicle is removed
 * with {@link RemovalType#REMOVE_RECALCULATE}.
 *
 * <p>This is a user-driven choice made at removal time, not a static per-route
 * configuration.  The frontend asks the operator which mode to use before
 * submitting the request.
 */
public enum RecalculationScope {

    /**
     * Redistribute departure times equitably among <em>all</em> remaining
     * vehicles in the operation.  The interval between the removed vehicle's
     * first qualifying departure and the last vehicle's first qualifying
     * departure is divided equally among all candidates.
     */
    ALL_VEHICLES,

    /**
     * Shift only the next {@code windowSize} vehicles forward (earlier) to
     * close the gap left by the removed vehicle.  Vehicles beyond the window
     * are unaffected; the gap reappears after the last shifted vehicle.
     */
    SUBSEQUENT_X
}
