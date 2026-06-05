package com.example.tgs_dev.service.removal;

/**
 * Canonical values for {@link com.example.tgs_dev.entity.VehicleAssignment#getRemovalReason()}.
 *
 * <p>See {@link VehicleAssignmentOrigin} for the rationale behind using
 * string constants instead of an enum.
 */
public final class VehicleRemovalReason {

    /** Default reason for {@code REMOVE_ONLY} and {@code REMOVE_RECALCULATE}. */
    public static final String REMOVED  = "REMOVED";

    /** Used when an assignment was replaced (REMOVE_REPLACE). */
    public static final String REPLACED = "REPLACED";

    /** Used when an assignment was loaned to another route as a replacement source. */
    public static final String LOANED   = "LOANED";

    private VehicleRemovalReason() {}
}
