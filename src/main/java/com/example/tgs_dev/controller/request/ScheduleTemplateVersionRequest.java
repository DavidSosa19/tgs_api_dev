package com.example.tgs_dev.controller.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Request body for creating or updating a
 * {@link com.example.tgs_dev.entity.ScheduleTemplateVersion}.
 *
 * <p>{@code label} is optional — useful for descriptive names like
 * "Horario vacacional dic-ene 2024" but not required for operation.
 * {@code effectiveTo} is nullable to represent open-ended versions.
 */
public record ScheduleTemplateVersionRequest(
        @Size(max = 100) String label,
        @NotNull LocalTime startTime,
        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveTo
) {}
