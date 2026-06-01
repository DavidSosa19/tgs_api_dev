package com.example.tgs_dev.service;

import com.example.tgs_dev.controller.exception.BusinessException;
import com.example.tgs_dev.controller.exception.ConflictException;
import com.example.tgs_dev.controller.exception.ResourceNotFoundException;
import com.example.tgs_dev.controller.request.RouteOperationalPeriodRequest;
import com.example.tgs_dev.controller.request.RouteTimeRangeRequest;
import com.example.tgs_dev.entity.Company;
import com.example.tgs_dev.entity.OperationalPeriodTimeRange;
import com.example.tgs_dev.entity.Route;
import com.example.tgs_dev.entity.RouteOperationalPeriod;
import com.example.tgs_dev.repository.RouteOperationalPeriodRepository;
import com.example.tgs_dev.repository.specification.CommonSpecifications;
import com.example.tgs_dev.repository.specification.TenantSpecifications;
import org.hibernate.Hibernate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Manages {@link RouteOperationalPeriod} records for the current tenant.
 *
 * <h3>Non-overlap invariant</h3>
 * <p>At most one active period may cover any given (route, company, date).
 * Both {@link #create} and {@link #update} call {@link #assertNoOverlap} before
 * persisting to enforce this invariant at the service layer.  The database should
 * additionally carry an EXCLUDE constraint for defence-in-depth.
 *
 * <h3>Mandatory periods</h3>
 * <p>Every scheduled operation <em>must</em> have an active period.
 * {@link ScheduleService} calls {@link #findActiveForDateOrThrow} and will
 * surface a {@link BusinessException} if no period is configured for the
 * operation date.  Use {@link #findActiveForDate} only when you want an
 * {@link Optional} and handle the absent case explicitly.
 */
@Service
public class RouteOperationalPeriodService {

    private final RouteOperationalPeriodRepository repository;
    private final RouteService                     routeService;
    private final TenantService                    tenantService;

    public RouteOperationalPeriodService(RouteOperationalPeriodRepository repository,
                                         RouteService routeService,
                                         TenantService tenantService) {
        this.repository    = repository;
        this.routeService  = routeService;
        this.tenantService = tenantService;
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    /**
     * Returns all active periods for the given route (tenant-scoped),
     * ordered by {@code effectiveFrom} ascending.
     *
     * @param routeId the route's primary key
     * @return list of periods, empty if none exist
     */
    @Transactional(readOnly = true)
    public List<RouteOperationalPeriod> findAllByRoute(Long groupId) {
        Route route = loadRouteOrThrow(groupId);
        return repository.findAllByRouteAndCompany(route, tenantService.currentCompanyId());
    }

    /**
     * Returns one period by its ID with the {@code timeRanges} collection eagerly
     * initialized, validated against the current tenant and the expected parent route.
     *
     * <p>With OSIV disabled ({@code spring.jpa.open-in-view=false}), accessing a
     * lazy collection on a detached entity in the controller layer throws
     * {@code LazyInitializationException}.  The {@code @Transactional(readOnly=true)}
     * keeps the session open until the method returns, allowing
     * {@link Hibernate#initialize} to hydrate the collection before the caller
     * maps the entity to a DTO.
     *
     * @param routeId  the parent route's ID (ownership check)
     * @param periodId the period's ID
     * @return the matching period with {@code timeRanges} initialized
     * @throws ResourceNotFoundException if not found or cross-tenant
     */
    @Transactional(readOnly = true)
    public RouteOperationalPeriod findById(Long groupId, Integer periodId) {
        RouteOperationalPeriod period = loadPeriodOrThrow(groupId, periodId);
        Hibernate.initialize(period.getTimeRanges());
        return period;
    }

    /**
     * Returns the period active on {@code date} for the given route and company,
     * or {@code empty()} if no period covers that date.
     *
     * @param route     the route being scheduled
     * @param companyId the current tenant's ID
     * @param date      the operation's service date
     * @return the active period, or empty if none is configured
     */
    public Optional<RouteOperationalPeriod> findActiveForDate(Route route,
                                                               Integer companyId,
                                                               LocalDate date) {
        return repository.findActiveForDate(route, companyId, date);
    }

    /**
     * Returns the period active on {@code date}, or throws if none is configured.
     *
     * <p>Periods are mandatory for schedule generation.  Every (route, company, date)
     * combination must have exactly one active period before schedules can be
     * calculated.
     *
     * @param route     the route being scheduled
     * @param companyId the current tenant's ID
     * @param date      the operation's service date
     * @return the active period
     * @throws BusinessException if no period covers the given date
     */
    public RouteOperationalPeriod findActiveForDateOrThrow(Route route,
                                                            Integer companyId,
                                                            LocalDate date) {
        return repository.findActiveForDate(route, companyId, date)
                .orElseThrow(() -> new BusinessException(
                        "validation.routeOperationalPeriod.noPeriodForDate|"
                        + route.getId() + "|" + date));
    }

    // ── Mutations ─────────────────────────────────────────────────────────────

    /**
     * Creates a new operational period for the given route.
     *
     * <p>When {@code request.useTimeRanges = true}, the time-range list is
     * validated via {@link TimeRangeValidator} and persisted atomically via the
     * cascade on {@link RouteOperationalPeriod#getTimeRanges()}.
     *
     * @param routeId the parent route's ID
     * @param request the creation request (validated by the controller)
     * @return the persisted period
     * @throws BusinessException if {@code effectiveTo < effectiveFrom} or
     *                           the time-range list is invalid
     * @throws ConflictException if the date range overlaps an existing period
     */
    @Transactional
    public RouteOperationalPeriod create(Long groupId,
                                         RouteOperationalPeriodRequest request) {
        Route   route   = loadRouteOrThrow(groupId);
        Company company = tenantService.currentCompany();

        assertValidDateRange(request.effectiveFrom(), request.effectiveTo());
        assertNoOverlap(route, request.effectiveFrom(), request.effectiveTo(), -1);

        RouteOperationalPeriod period = new RouteOperationalPeriod(
                route,
                company,
                request.label(),
                request.baseDuration(),
                request.cycleCount(),
                request.effectiveFrom(),
                request.effectiveTo()
        );

        boolean useRanges = Boolean.TRUE.equals(request.useTimeRanges());
        period.setUseTimeRanges(useRanges);
        if (useRanges) {
            TimeRangeValidator.validate(request.timeRanges(), "period");
            applyTimeRanges(period, request.timeRanges(), company);
        }

        return repository.save(period);
    }

    /**
     * Updates an existing period's parameters, replacing the time-range list
     * atomically when {@code useTimeRanges} is active.
     *
     * @param routeId  the parent route's ID (ownership check)
     * @param periodId the period to update
     * @param request  the update request
     * @return the updated period
     * @throws BusinessException if {@code effectiveTo < effectiveFrom} or
     *                           the time-range list is invalid
     * @throws ConflictException if the new date range overlaps another period
     */
    @Transactional
    public RouteOperationalPeriod update(Long groupId, Integer periodId,
                                          RouteOperationalPeriodRequest request) {
        RouteOperationalPeriod period = loadPeriodOrThrow(groupId, periodId);

        assertValidDateRange(request.effectiveFrom(), request.effectiveTo());
        assertNoOverlap(period.getRoute(), request.effectiveFrom(), request.effectiveTo(), periodId);

        period.setLabel(request.label());
        period.setBaseDuration(request.baseDuration());
        period.setCycleCount(request.cycleCount());
        period.setEffectiveFrom(request.effectiveFrom());
        period.setEffectiveTo(request.effectiveTo());

        // Replace time ranges atomically; orphan-removal deletes the old ones.
        boolean useRanges = Boolean.TRUE.equals(request.useTimeRanges());
        period.setUseTimeRanges(useRanges);
        period.getTimeRanges().clear();
        if (useRanges) {
            TimeRangeValidator.validate(request.timeRanges(), "period");
            applyTimeRanges(period, request.timeRanges(), period.getCompany());
        }

        return repository.save(period);
    }

    /**
     * Soft-deletes a period.
     *
     * @param routeId  the parent route's ID (ownership check)
     * @param periodId the period to delete
     */
    @Transactional
    public void delete(Long groupId, Integer periodId) {
        RouteOperationalPeriod period = loadPeriodOrThrow(groupId, periodId);
        repository.softDelete(period);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Route loadRouteOrThrow(Long groupId) {
        // findByGroupId resolves the current version for this group — SCD-aware.
        return routeService.findByGroupId(groupId);
    }

    private RouteOperationalPeriod loadPeriodOrThrow(Long groupId, Integer periodId) {
        Integer companyId = tenantService.currentCompanyId();
        // Resolve current route version so the ownership check uses the right entity id.
        Route route = loadRouteOrThrow(groupId);
        return repository.findOne(
                Specification.<RouteOperationalPeriod>where(
                        CommonSpecifications.fieldEquals("id", periodId))
                        .and(TenantSpecifications.belongsToCompany(companyId))
        ).filter(p -> p.getRoute().getId().equals(route.getId()))
         .orElseThrow(() -> new ResourceNotFoundException(
                 "notFound.routeOperationalPeriod|" + periodId));
    }

    /**
     * Builds {@link OperationalPeriodTimeRange} entities from the request list,
     * sets both sides of the bidirectional association, and appends them to the
     * period's collection.  Sort order is assigned sequentially (1-based).
     */
    private void applyTimeRanges(RouteOperationalPeriod period,
                                  List<RouteTimeRangeRequest> ranges,
                                  Company company) {
        for (int i = 0; i < ranges.size(); i++) {
            RouteTimeRangeRequest r = ranges.get(i);
            OperationalPeriodTimeRange entity = new OperationalPeriodTimeRange(
                    r.rangeStart(), r.rangeEnd(), r.durationMinutes(), i + 1, r.crossesMidnight());
            entity.setPeriod(period);
            entity.setCompany(company);
            period.getTimeRanges().add(entity);
        }
    }

    private void assertValidDateRange(LocalDate effectiveFrom, LocalDate effectiveTo) {
        if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw new BusinessException("validation.routeOperationalPeriod.invalidDateRange");
        }
    }

    private void assertNoOverlap(Route route, LocalDate effectiveFrom,
                                  LocalDate effectiveTo, Integer excludeId) {
        List<RouteOperationalPeriod> overlapping = repository.findOverlapping(
                route, tenantService.currentCompanyId(),
                effectiveFrom, effectiveTo, excludeId);
        if (!overlapping.isEmpty()) {
            throw new ConflictException("conflict.routeOperationalPeriod.overlap");
        }
    }
}
