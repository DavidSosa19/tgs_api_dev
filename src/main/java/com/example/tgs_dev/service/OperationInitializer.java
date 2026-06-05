package com.example.tgs_dev.service;

import com.example.tgs_dev.entity.Route;
import com.example.tgs_dev.entity.RouteOperation;
import com.example.tgs_dev.entity.VehicleAssignment;
import com.example.tgs_dev.service.strategy.AssignmentSlot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Per-route transactional unit for operation initialisation, extracted from
 * {@link OperationOrchestratorService} to enable independent transaction
 * boundaries when {@code initAllOperations} processes many routes.
 *
 * <h3>Why a separate bean</h3>
 * Spring's {@code @Transactional} is implemented via JDK / CGLIB proxies.  When
 * a {@code @Transactional} method calls another {@code @Transactional} method
 * on {@code this}, the second annotation is bypassed because the call doesn't
 * go through the proxy.  Putting {@link #persistOne} in a separate Spring bean
 * forces every call to traverse the proxy and start a fresh transaction.
 *
 * <h3>Transaction semantics</h3>
 * Each call to {@link #persistOne} runs in its OWN transaction
 * ({@link Propagation#REQUIRES_NEW}).  Failure of one route does not roll back
 * the routes that were initialised successfully before it — the orchestrator
 * catches and logs the exception and continues with the next route.
 */
@Service
public class OperationInitializer {

    private final RouteOperationService    routeOperationService;
    private final VehicleAssignmentService vehicleAssignmentService;
    private final ScheduleService          scheduleService;

    public OperationInitializer(RouteOperationService routeOperationService,
                                VehicleAssignmentService vehicleAssignmentService,
                                ScheduleService scheduleService) {
        this.routeOperationService    = routeOperationService;
        this.vehicleAssignmentService = vehicleAssignmentService;
        this.scheduleService          = scheduleService;
    }

    /**
     * Persists the operation, its vehicle assignments and the generated schedules
     * for a single {@code route} on {@code date}, in an isolated transaction.
     *
     * @param route the route being initialised
     * @param date  the service date
     * @param slots the pre-resolved assignment slots for this route
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistOne(Route route, LocalDate date, List<AssignmentSlot> slots) {
        RouteOperation          operation   = routeOperationService.initRouteOperation(route, date);
        List<VehicleAssignment> assignments = vehicleAssignmentService.assignVehicles(slots, operation);
        scheduleService.calculateVehicleSchedules(assignments);
    }
}
