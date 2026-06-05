package com.example.tgs_dev.service;

import com.example.tgs_dev.controller.response.RouteOperationDTO;
import com.example.tgs_dev.entity.Route;
import com.example.tgs_dev.entity.RouteOperation;
import com.example.tgs_dev.entity.VehicleAssignment;
import com.example.tgs_dev.repository.RouteOperationRepository;
import com.example.tgs_dev.repository.specification.CommonSpecifications;
import com.example.tgs_dev.repository.specification.TenantSpecifications;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
public class RouteOperationService {

    private final RouteOperationRepository  routeOperationRepository;
    private final VehicleAssignmentService  vehicleAssignmentService;
    private final TenantService             tenantService;

    public RouteOperationService(RouteOperationRepository routeOperationRepository,
                                 @Lazy VehicleAssignmentService vehicleAssignmentService,
                                 TenantService tenantService) {
        this.routeOperationRepository = routeOperationRepository;
        this.vehicleAssignmentService = vehicleAssignmentService;
        this.tenantService            = tenantService;
    }

    public RouteOperation save(RouteOperation routeOperation) {
        routeOperation.setCompany(tenantService.currentCompany());
        return routeOperationRepository.save(routeOperation);
    }

    public RouteOperation findById(Integer id) {
        return routeOperationRepository.findOne(
                Specification.<RouteOperation>where(CommonSpecifications.fieldEquals("id", id))
                             .and(TenantSpecifications.belongsToCompany(tenantService.currentCompanyId()))
        ).orElseThrow(() -> new NoSuchElementException("notFound.routeOperation|" + id));
    }

    public List<RouteOperation> findAll() {
        return routeOperationRepository.findAll(
                TenantSpecifications.belongsToCompany(tenantService.currentCompanyId()));
    }

    /**
     * Returns all operations for the given date as DTOs, with the {@code route}
     * association eagerly fetched in a single query.
     *
     * <p>{@code @Transactional(readOnly = true)} keeps the Hibernate session open
     * while the entities are mapped to DTOs.  The JOIN FETCH in the repository
     * query avoids N+1 selects.  Together these two measures prevent
     * {@code LazyInitializationException} with OSIV disabled
     * ({@code spring.jpa.open-in-view=false}).
     */
    @Transactional(readOnly = true)
    public List<RouteOperationDTO> findAllByDate(LocalDate date) {
        return routeOperationRepository
                .findAllByDateAndCompany(date, tenantService.currentCompanyId())
                .stream()
                .map(RouteOperationDTO::from)
                .toList();
    }

    /**
     * Used internally (e.g. soft-delete cascade); returns raw entities within
     * an existing transaction context.
     */
    public List<RouteOperation> findAllByDateRaw(LocalDate date) {
        return routeOperationRepository.findAll(
                CommonSpecifications.<RouteOperation>fieldEquals("serviceDate", date)
                                    .and(TenantSpecifications.belongsToCompany(tenantService.currentCompanyId())));
    }

    /**
     * Loads the operation with a row-level pessimistic write lock.  Used at
     * the entry point of mutating flows (vehicle removal / recalculation) so
     * concurrent operations on the same row serialise.  Must be called within
     * an active transaction.
     *
     * @throws NoSuchElementException if the operation does not exist or is not visible to the tenant
     */
    public RouteOperation findByIdForUpdate(Integer id) {
        RouteOperation op = routeOperationRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NoSuchElementException("notFound.routeOperation|" + id));
        // Defence-in-depth tenant check — lock was acquired before this, so it's authoritative
        if (!op.getCompany().getId().equals(tenantService.currentCompanyId())) {
            throw new NoSuchElementException("notFound.routeOperation|" + id);
        }
        return op;
    }

    public Optional<RouteOperation> findByRouteAndDate(Route route, LocalDate date) {
        return routeOperationRepository.findOne(
                CommonSpecifications.<RouteOperation>fieldEquals("route", route)
                                    .and(CommonSpecifications.fieldEquals("serviceDate", date))
                                    .and(TenantSpecifications.belongsToCompany(tenantService.currentCompanyId())));
    }

    @Transactional
    public void softDelete(RouteOperation operation) {
        List<VehicleAssignment> assignments = vehicleAssignmentService.findByRouteOperation(operation);
        vehicleAssignmentService.softDeleteAll(assignments);
        routeOperationRepository.softDelete(operation);
    }

    @Transactional
    public void softDeleteAllByDate(LocalDate date) {
        List<RouteOperation> operations = findAllByDateRaw(date);
        if (operations.isEmpty()) return;
        for (RouteOperation op : operations) {
            List<VehicleAssignment> assignments = vehicleAssignmentService.findByRouteOperation(op);
            vehicleAssignmentService.softDeleteAll(assignments);
        }
        routeOperationRepository.softDeleteAll(operations);
    }

    @Transactional
    public RouteOperation initRouteOperation(Route route, LocalDate date) {
        RouteOperation newRouteOperation = new RouteOperation(route, date);
        return save(newRouteOperation);
    }
}
