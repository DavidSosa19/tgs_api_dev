package com.example.tgs_dev.service;

import com.example.tgs_dev.controller.exception.BusinessException;
import com.example.tgs_dev.controller.request.RemoveVehicleRequest;
import com.example.tgs_dev.entity.RouteOperation;
import com.example.tgs_dev.entity.VehicleAssignment;
import com.example.tgs_dev.entity.enums.RemovalType;
import com.example.tgs_dev.service.removal.RemovalContext;
import com.example.tgs_dev.service.removal.RemovalOutcome;
import com.example.tgs_dev.service.removal.VehicleRemovalStrategy;
import com.example.tgs_dev.service.removal.VehicleRemovedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toUnmodifiableMap;

/**
 * Orchestrates vehicle removal by resolving the assignment, taking a
 * pessimistic write lock on the parent {@link RouteOperation} (so concurrent
 * removals serialise), and dispatching to the matching
 * {@link VehicleRemovalStrategy}.
 *
 * <p>Owns no business logic — its responsibilities are:
 * <ol>
 *   <li>Tenant-scoped {@link VehicleAssignment} lookup.</li>
 *   <li>{@code PESSIMISTIC_WRITE} lock on the parent {@link RouteOperation}.</li>
 *   <li>{@link RemovalContext} construction (with a single {@code now}
 *       timestamp reused by every strategy for consistent audit data).</li>
 *   <li>Strategy dispatch (with explicit error when no strategy is registered).</li>
 *   <li>Audit logging and {@link VehicleRemovedEvent} emission.</li>
 * </ol>
 *
 * <h3>Adding a new removal mode</h3>
 * Register a new {@link VehicleRemovalStrategy} Spring bean and add the
 * corresponding value to {@link RemovalType}.  This service requires no
 * changes.
 */
@Service
public class VehicleRemovalService {

    private static final Logger log = LoggerFactory.getLogger(VehicleRemovalService.class);

    private final Map<RemovalType, VehicleRemovalStrategy> strategies;
    private final VehicleAssignmentService                 vehicleAssignmentService;
    private final RouteOperationService                    routeOperationService;
    private final ApplicationEventPublisher                eventPublisher;

    public VehicleRemovalService(List<VehicleRemovalStrategy> strategies,
                                 VehicleAssignmentService vehicleAssignmentService,
                                 RouteOperationService routeOperationService,
                                 ApplicationEventPublisher eventPublisher) {
        this.strategies               = strategies.stream()
                .collect(toUnmodifiableMap(VehicleRemovalStrategy::supports, identity()));
        this.vehicleAssignmentService = vehicleAssignmentService;
        this.routeOperationService    = routeOperationService;
        this.eventPublisher           = eventPublisher;
    }

    @Transactional
    public void handleRemoval(RemoveVehicleRequest request) {
        LocalDateTime now = LocalDateTime.now();

        VehicleAssignment assignment = vehicleAssignmentService
                .findById(request.vehicleAssignmentId())
                .orElseThrow(() -> new NoSuchElementException(
                        "notFound.vehicleAssignment|" + request.vehicleAssignmentId()));

        // Pessimistic-lock the parent operation — concurrent removals on the
        // same operation will serialise instead of racing.  Loaded only for
        // its locking effect; the strategy continues to use the operation
        // referenced by the assignment.
        routeOperationService.findByIdForUpdate(assignment.getRouteOperation().getId());

        VehicleRemovalStrategy strategy = strategies.get(request.removalType());
        if (strategy == null) {
            throw new BusinessException("unsupported.removalType|" + request.removalType());
        }

        log.info("Vehicle removal requested: assignment={}, type={}, operation={}, fromTime={}",
                 assignment.getId(), request.removalType(),
                 assignment.getRouteOperation().getId(), request.fromTime());

        RemovalOutcome outcome = strategy.execute(new RemovalContext(
                assignment,
                now,
                request.fromTime(),
                request.recalculationScope(),
                request.windowSize(),
                request.sourceRouteGroupId()
        ));

        eventPublisher.publishEvent(new VehicleRemovedEvent(
                assignment.getCompany() != null ? assignment.getCompany().getId() : null,
                assignment.getId(),
                assignment.getRouteOperation().getId(),
                assignment.getVehicle() != null ? assignment.getVehicle().getId() : null,
                request.removalType(),
                outcome.replacementAssignmentId(),
                now
        ));
    }
}
