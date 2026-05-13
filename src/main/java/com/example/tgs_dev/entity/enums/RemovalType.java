package com.example.tgs_dev.entity.enums;

public enum RemovalType {
    /** Soft delete only — no schedule recalculation. */
    REMOVE_ONLY,

    /** Soft delete + redistribute departure times for vehicles after the removed one. */
    REMOVE_RECALCULATE,

    /** Soft delete + replace with a vehicle from another operation (temporary: route 3). */
    REMOVE_REPLACE
}
