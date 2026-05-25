package com.example.tgs_dev.service.strategy;

import com.example.tgs_dev.entity.ScheduleTemplate;
import com.example.tgs_dev.entity.Vehicle;

/**
 * Minimal projection of a single vehicle-to-template assignment slot.
 *
 * <p>Decouples {@link ScheduleInitStrategy} implementations from
 * {@link com.example.tgs_dev.entity.RotationEntry}, which is an
 * {@code @Entity} tied to the rotation-based model and carries a mandatory
 * FK to {@code VehicleRotation}.  A future strategy that does not use
 * rotations can produce its own lightweight implementation without touching
 * the persistence layer.
 *
 * <h3>Contract</h3>
 * <ul>
 *   <li>Both {@link #getVehicle()} and {@link #getScheduleTemplate()} must
 *       return non-null values.</li>
 *   <li>Implementations are not required to be {@code @Entity} — plain
 *       records or POJOs are fine.</li>
 * </ul>
 */
public interface AssignmentSlot {

    /** The vehicle that will be assigned to the schedule slot. */
    Vehicle getVehicle();

    /** The schedule template defining the route and start time for the slot. */
    ScheduleTemplate getScheduleTemplate();
}
