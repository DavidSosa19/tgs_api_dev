package com.example.tgs_dev.controller.response.viewer;

import java.util.List;

/**
 * Root response for the operation schedule endpoint.
 *
 * <p>Contains the contextual header (route, date) and the ordered list of
 * vehicle rows with their departure schedules.  The structure is designed for
 * direct rendering — no client-side grouping or sorting is required.
 *
 * <h3>Future caching</h3>
 * <p>This record is intentionally serialization-friendly (all fields are
 * primitives, {@link java.time.LocalDate}, {@link java.time.LocalTime} or
 * nested records).  When Redis caching is added, this object can be cached
 * as-is using a JSON or Kryo serializer without any structural changes.
 * The cache key must include the tenant company ID — see
 * {@code MatrixService.getOperationSchedules} for the designated hook point.
 */
public record OperationScheduleDTO(
        OperationHeaderDTO       operation,
        List<VehicleScheduleDTO> vehicleSchedules
) {
    /**
     * Returns an empty schedule for an operation that has no vehicle assignments.
     *
     * <p>Returning {@code HTTP 200} with an empty list (rather than {@code 404})
     * is intentional: an operation can legitimately have no vehicles assigned yet,
     * and the frontend needs to distinguish "not found" from "found but empty".
     */
    public static OperationScheduleDTO empty(OperationHeaderDTO header) {
        return new OperationScheduleDTO(header, List.of());
    }
}
