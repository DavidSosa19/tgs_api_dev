package com.example.tgs_dev.service.removal;

import com.example.tgs_dev.entity.enums.RemovalType;

import java.time.LocalDateTime;

/**
 * Domain event emitted after a vehicle assignment has been successfully removed.
 *
 * <p>Published by {@link com.example.tgs_dev.service.VehicleRemovalService} via
 * Spring's {@code ApplicationEventPublisher} so downstream consumers
 * (audit logs, passenger notifications, reporting pipelines) can react without
 * coupling to the removal flow.
 *
 * @param companyId           tenant the removal belongs to
 * @param assignmentId        ID of the removed assignment
 * @param routeOperationId    operation the assignment belonged to
 * @param vehicleId           vehicle that was removed
 * @param removalType         mode used (REMOVE_ONLY / REMOVE_RECALCULATE / REMOVE_REPLACE)
 * @param replacementId       ID of the replacement assignment (REMOVE_REPLACE only), {@code null} otherwise
 * @param occurredAt          server-side timestamp when the removal completed
 */
public record VehicleRemovedEvent(
        Integer       companyId,
        Integer       assignmentId,
        Integer       routeOperationId,
        Integer       vehicleId,
        RemovalType   removalType,
        Integer       replacementId,
        LocalDateTime occurredAt
) {}
