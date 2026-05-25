package com.example.tgs_dev.controller.response.viewer;

import java.time.LocalTime;

/**
 * A single departure entry within a vehicle's daily schedule.
 *
 * <p>Carries only the information the consumer needs — order and time.
 * No entity state, no audit fields, no back-references.
 */
public record ScheduleEntryDTO(
        Integer   departureOrder,
        LocalTime departureTime
) {}
