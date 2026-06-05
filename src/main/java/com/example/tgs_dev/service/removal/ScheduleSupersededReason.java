package com.example.tgs_dev.service.removal;

/**
 * Canonical reasons a {@link com.example.tgs_dev.entity.Schedule} row was
 * marked inactive ({@code active = false}).
 *
 * <p>Mapped to the {@code core.schedule.superseded_reason} VARCHAR column.
 * The set of valid values is enforced by a database CHECK constraint — keep
 * these constants in sync with the SQL definition (V22 migration).
 */
public final class ScheduleSupersededReason {

    /** The vehicle assignment was removed entirely (REMOVE_ONLY / REMOVE_RECALCULATE). */
    public static final String VEHICLE_REMOVED = "VEHICLE_REMOVED";

    /** Superseded by a new row created during a recalculation. */
    public static final String RECALCULATED    = "RECALCULATED";

    /** Superseded by a replacement vehicle (REMOVE_REPLACE) — applies to the eliminated vehicle's schedules. */
    public static final String REPLACED        = "REPLACED";

    /** Donor's schedules that won't run because the vehicle was loaned to another operation. */
    public static final String LOANED          = "LOANED";

    /** Backfilled by V22 migration — schedules of already soft-deleted assignments. */
    public static final String LEGACY_REMOVAL  = "LEGACY_REMOVAL";

    /** Reserved for future manual edits made by operators. */
    public static final String MANUAL          = "MANUAL";

    private ScheduleSupersededReason() {}
}
