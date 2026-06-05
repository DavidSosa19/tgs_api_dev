package com.example.tgs_dev.service.removal;

/**
 * Canonical values for {@link com.example.tgs_dev.entity.VehicleAssignment#getOrigin()}.
 *
 * <p>String constants instead of an enum because the column is already typed as
 * {@code VARCHAR} in the schema and may be extended by ad-hoc operational
 * processes (data imports, manual fixes) without code changes.
 */
public final class VehicleAssignmentOrigin {

    /** Created by the scheduler during operation initialisation. */
    public static final String ORIGINAL    = "ORIGINAL";

    /** Created as a replacement for an assignment removed via REMOVE_REPLACE. */
    public static final String REPLACEMENT = "REPLACEMENT";

    private VehicleAssignmentOrigin() {}
}
