package com.example.tgs_dev.entity.enums;

/**
 * How a {@link com.example.tgs_dev.entity.Schedule} row was created.
 *
 * <p>Mapped to the {@code core.schedule.origin} VARCHAR column.  The set of
 * valid values is enforced by a database CHECK constraint — keep this enum in
 * sync with the SQL definition (V22 migration).
 */
public enum ScheduleOrigin {

    /** Created during operation initialisation — represents the initial plan. */
    ORIGINAL,

    /** Created by a {@code REMOVE_RECALCULATE} operation — shifted from an earlier row. */
    RECALCULATED,

    /** Created by a {@code REMOVE_REPLACE} operation — inherited from the replaced vehicle. */
    REPLACEMENT,

    /** Reserved for future manual edits made by operators. */
    MANUAL
}
