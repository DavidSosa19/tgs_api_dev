package com.example.tgs_dev.service;

import com.example.tgs_dev.controller.exception.BusinessException;
import com.example.tgs_dev.controller.exception.ResourceNotFoundException;
import com.example.tgs_dev.controller.request.RouteRequest;
import com.example.tgs_dev.entity.Company;
import com.example.tgs_dev.entity.Route;
import com.example.tgs_dev.entity.RouteGroup;
import com.example.tgs_dev.repository.RouteGroupRepository;
import com.example.tgs_dev.repository.RouteOperationRepository;
import com.example.tgs_dev.repository.RouteRepository;
import com.example.tgs_dev.repository.ScheduleTemplateRepository;
import com.example.tgs_dev.repository.filter.FilterRequest;
import com.example.tgs_dev.repository.specification.CommonSpecifications;
import com.example.tgs_dev.repository.specification.TenantSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service for {@link Route} entities — SCD Type-2 aware.
 *
 * <p>See {@link PersonService} for the full SCD mutation pattern description.
 */
@Service
public class RouteService {

    private final RouteRepository            routeRepository;
    private final RouteGroupRepository       routeGroupRepository;
    private final ScheduleTemplateRepository scheduleTemplateRepository;
    private final RouteOperationRepository   routeOperationRepository;
    private final TenantService              tenantService;

    public RouteService(RouteRepository            routeRepository,
                        RouteGroupRepository       routeGroupRepository,
                        ScheduleTemplateRepository scheduleTemplateRepository,
                        RouteOperationRepository   routeOperationRepository,
                        TenantService              tenantService) {
        this.routeRepository            = routeRepository;
        this.routeGroupRepository       = routeGroupRepository;
        this.scheduleTemplateRepository = scheduleTemplateRepository;
        this.routeOperationRepository   = routeOperationRepository;
        this.tenantService              = tenantService;
    }

    // ── Navigation (SCD-aware) ────────────────────────────────────────────────

    /**
     * Returns the current active version for the given group.
     * Used for {@code GET /routes/{groupId}}.
     *
     * @throws ResourceNotFoundException if no active current version exists
     */
    @Transactional(readOnly = true)
    public Route findByGroupId(Long groupId) {
        return routeRepository
                .findCurrentByGroupId(groupId, tenantService.currentCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("notFound.route|" + groupId));
    }

    /**
     * Returns all current active routes for the company, sorted numerically.
     * Used by scheduling and route selector dropdowns.
     */
    @Transactional(readOnly = true)
    public List<Route> findAll() {
        return routeRepository.findAllActiveByCompanySorted(tenantService.currentCompanyId());
    }

    /**
     * Returns all current versions (active + inactive) for the listing UI.
     */
    @Transactional(readOnly = true)
    public List<Route> findAllCurrent() {
        return routeRepository.findAllCurrentByCompany(tenantService.currentCompanyId());
    }

    // ── Internal / FK resolution ──────────────────────────────────────────────

    /**
     * Finds by entity surrogate ID — for internal FK resolution only.
     * Prefer {@link #findByGroupId(Long)} for user-facing navigation.
     */
    @Transactional(readOnly = true)
    public Route findById(Integer id) {
        Integer companyId = tenantService.currentCompanyId();
        return routeRepository.findOne(
                Specification.<Route>where(CommonSpecifications.fieldEquals("id", id))
                        .and(TenantSpecifications.belongsToCompany(companyId))
                        .and(TenantSpecifications.isActive())
        ).orElseThrow(() -> new ResourceNotFoundException("notFound.route|" + id));
    }

    @Transactional(readOnly = true)
    public Optional<Route> findByNumber(String routeNumber) {
        return routeRepository.findOne(
                CommonSpecifications.<Route>fieldEquals("routeNumber", routeNumber)
                        .and(TenantSpecifications.belongsToCompany(tenantService.currentCompanyId()))
                        .and(TenantSpecifications.isActive()));
    }

    // ── Mutations ─────────────────────────────────────────────────────────────

    /**
     * Creates a new route group and its first version.
     */
    @Transactional
    public Route create(RouteRequest request) {
        Company company = tenantService.currentCompany();

        RouteGroup group = routeGroupRepository.save(
                new RouteGroup(company, request.routeNumber()));

        Route route = new Route(request.routeNumber());
        route.setCompany(company);
        route.setGroup(group);
        route.setVersionFrom(LocalDateTime.now());
        route.setVersionTo(null);
        route.setIsCurrent(true);
        return routeRepository.save(route);
    }

    /**
     * Updates a route by closing the current version and opening a new one.
     */
    @Transactional
    public Route update(Long groupId, RouteRequest request) {
        Integer companyId = tenantService.currentCompanyId();
        Route current = routeRepository
                .findCurrentByGroupId(groupId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("notFound.route|" + groupId));

        LocalDateTime now = LocalDateTime.now();
        current.setVersionTo(now);
        current.setIsCurrent(false);
        routeRepository.save(current);

        Route next = new Route(request.routeNumber());
        next.setCompany(current.getCompany());
        next.setGroup(current.getGroup());
        next.setVersionFrom(now);
        next.setVersionTo(null);
        next.setIsCurrent(true);
        return routeRepository.save(next);
    }

    /**
     * Deactivates the current version after checking FK constraints.
     */
    @Transactional
    public void deactivate(Long groupId) {
        Integer companyId = tenantService.currentCompanyId();
        Route current = routeRepository
                .findCurrentByGroupId(groupId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("notFound.route|" + groupId));

        Integer id = current.getId();
        if (scheduleTemplateRepository.existsByRouteIdAndActiveTrue(id)) {
            throw new BusinessException("fk.routeInTemplate");
        }
        if (scheduleTemplateRepository.existsBySecondaryRouteIdAndActiveTrue(id)) {
            throw new BusinessException("fk.routeSecondaryInTemplate");
        }
        if (routeOperationRepository.existsByRouteIdAndActiveTrue(id)) {
            throw new BusinessException("fk.routeInOperation");
        }
        routeRepository.softDelete(current);
    }

    @Transactional(readOnly = true)
    public Page<Route> filter(FilterRequest request) {
        return routeRepository.filter(
                request,
                request.toPageable(),
                TenantSpecifications.<Route>belongsToCompany(tenantService.currentCompanyId())
                        .and(TenantSpecifications.isActive()));
    }
}
