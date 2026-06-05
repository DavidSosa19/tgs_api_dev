package com.example.tgs_dev.entity.enums;

public enum RemovalType {
    /** Soft delete only — no schedule recalculation. */
    REMOVE_ONLY,

    /** Soft delete + redistribute departure times for vehicles after the removed one. */
    REMOVE_RECALCULATE,

    /** Soft delete + replace with a vehicle from a donor route specified in the request. */
    REMOVE_REPLACE
}
