package com.example.tgs_dev.service.removal;

/**
 * Result of executing a {@link VehicleRemovalStrategy}.
 *
 * <p>Carries strategy-specific outputs the orchestrator needs after dispatch
 * (e.g. the replacement assignment ID for event emission).  Most modes return
 * {@link #empty()}; only {@code REMOVE_REPLACE} populates the replacement.
 *
 * @param replacementAssignmentId ID of the replacement created by REMOVE_REPLACE, or {@code null}
 */
public record RemovalOutcome(Integer replacementAssignmentId) {

    private static final RemovalOutcome EMPTY = new RemovalOutcome(null);

    /** Outcome with no strategy-specific output. */
    public static RemovalOutcome empty() { return EMPTY; }

    /** Outcome carrying the replacement assignment's ID. */
    public static RemovalOutcome withReplacement(Integer id) {
        return new RemovalOutcome(id);
    }
}
