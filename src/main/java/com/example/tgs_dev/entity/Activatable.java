package com.example.tgs_dev.entity;

/**
 * Marks an entity that supports logical deletion via an {@code active} flag.
 * Implemented by every entity that has an {@code active} column so that
 * {@code BaseRepositoryImpl} can toggle the flag without resorting to
 * dynamic JPQL string concatenation.
 */
public interface Activatable {
    void setActive(Boolean active);
}
