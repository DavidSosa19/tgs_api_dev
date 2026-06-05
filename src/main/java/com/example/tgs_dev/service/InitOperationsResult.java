package com.example.tgs_dev.service;

import java.util.List;

/**
 * Outcome of an {@link OperationOrchestratorService#initAllOperations} invocation.
 *
 * <p>Bulk initialisation has three possible per-route outcomes — initialised,
 * skipped (already had an operation for the date), or failed.  This record
 * captures all three so callers can render an accurate response instead of
 * silently dropping failures.
 *
 * <h3>Outcome classification</h3>
 * <ul>
 *   <li>{@link #isFullSuccess()} — every pending route was initialised; no failures.</li>
 *   <li>{@link #isPartial()}     — at least one initialised AND at least one failed.</li>
 *   <li>{@link #isAllFailed()}   — nothing initialised and at least one failed.</li>
 *   <li>{@link #isNoop()}        — nothing to do (no routes, or all already initialised).</li>
 * </ul>
 *
 * <p>{@code skipped} is informational (idempotency hint for the caller) and does
 * not affect the success classification.
 */
public record InitOperationsResult(
        int                       initialized,
        int                       skipped,
        List<RouteInitFailure>    failures
) {

    /** Canonical empty result used for "no routes" / "all already initialised" cases. */
    public static InitOperationsResult noop(int skipped) {
        return new InitOperationsResult(0, skipped, List.of());
    }

    public InitOperationsResult {
        // Defensive copy — failures must be immutable from the caller's perspective.
        failures = List.copyOf(failures);
    }

    public int total()      { return initialized + skipped + failures.size(); }
    public int failedCount(){ return failures.size(); }

    public boolean isFullSuccess() { return failures.isEmpty() && initialized > 0; }
    public boolean isPartial()     { return initialized > 0 && !failures.isEmpty(); }
    public boolean isAllFailed()   { return initialized == 0 && !failures.isEmpty(); }
    public boolean isNoop()        { return initialized == 0 && failures.isEmpty(); }

    /**
     * Per-route failure detail.  {@code reason} is the i18n message key (with any
     * {@code |}-separated parameters) of the underlying business exception, or the
     * exception class name + message for unexpected errors.  The frontend resolves
     * the key to localised text.
     */
    public record RouteInitFailure(
            Long   routeGroupId,
            String routeNumber,
            String reason
    ) {}
}
