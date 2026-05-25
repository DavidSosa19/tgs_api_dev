package com.example.tgs_dev.service;

import com.example.tgs_dev.controller.exception.ResourceNotFoundException;
import com.example.tgs_dev.controller.request.RouteCalendarOverrideRequest;
import com.example.tgs_dev.controller.request.RouteTimeRangeRequest;
import com.example.tgs_dev.entity.Company;
import com.example.tgs_dev.entity.Route;
import com.example.tgs_dev.entity.RouteCalendarOverride;
import com.example.tgs_dev.entity.RouteCalendarOverrideRange;
import com.example.tgs_dev.repository.RouteCalendarOverrideRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Manages {@link RouteCalendarOverride} instances for a tenant.
 *
 * <p>Upsert semantics: saving an override for a (route, date) pair that already
 * has an override replaces the existing one atomically (ranges use orphanRemoval).
 */
@Service
public class RouteCalendarOverrideService {

    private final RouteCalendarOverrideRepository overrideRepository;
    private final TenantService                   tenantService;
    private final RouteService                    routeService;

    public RouteCalendarOverrideService(RouteCalendarOverrideRepository overrideRepository,
                                        TenantService tenantService,
                                        RouteService routeService) {
        this.overrideRepository = overrideRepository;
        this.tenantService      = tenantService;
        this.routeService       = routeService;
    }

    // ── Resolver-facing API (called from CalendarOverrideDurationResolver) ─────

    /**
     * Returns the active override for a given route and operation date, scoped to
     * the current tenant.  Result includes ranges (fetched eagerly).
     */
    public Optional<RouteCalendarOverride> findByRouteAndDate(Route route, LocalDate date) {
        Integer companyId = tenantService.currentCompanyId();
        return overrideRepository.findByRouteAndDateAndCompany(route.getId(), date, companyId);
    }

    // ── Management API (called from controller) ───────────────────────────────

    /** Returns all overrides for a given route (management listing). */
    public List<RouteCalendarOverride> findAllByRoute(Integer routeId) {
        Route route = routeService.findById(routeId); // tenant-scoped
        return overrideRepository.findAll(
                (root, query, cb) -> cb.and(
                        cb.equal(root.get("route").get("id"), route.getId()),
                        cb.equal(root.get("company").get("id"), tenantService.currentCompanyId())));
    }

    /** Finds a single override by its own PK, tenant-scoped. */
    public RouteCalendarOverride findById(Integer id) {
        return overrideRepository.findOne(
                (root, query, cb) -> cb.and(
                        cb.equal(root.get("id"), id),
                        cb.equal(root.get("company").get("id"), tenantService.currentCompanyId()))
        ).orElseThrow(() -> new ResourceNotFoundException("notFound.calendarOverride|" + id));
    }

    /**
     * Creates or replaces the override for the given route + date.
     * Uses upsert semantics: if an override already exists for (routeId, date), it
     * is fully replaced.
     */
    @Transactional
    public RouteCalendarOverride save(Integer routeId, RouteCalendarOverrideRequest request) {
        Company company = tenantService.currentCompany();
        Route   route   = routeService.findById(routeId);

        if (Boolean.TRUE.equals(request.useTimeRanges())) {
            TimeRangeValidator.validate(request.ranges(), "calendarOverride");
        }

        // Upsert: replace if exists, create if not.
        RouteCalendarOverride override = overrideRepository
                .findByRouteAndDateAndCompany(route.getId(), request.overrideDate(), company.getId())
                .orElseGet(() -> new RouteCalendarOverride(
                        route, company, request.overrideDate(),
                        request.useTimeRanges(), request.baseDuration()));

        override.setUseTimeRanges(request.useTimeRanges());
        override.setBaseDuration(request.baseDuration());
        override.getRanges().clear();

        if (Boolean.TRUE.equals(request.useTimeRanges()) && request.ranges() != null) {
            addRanges(override, request.ranges(), company);
        }

        return overrideRepository.save(override);
    }

    /** Deletes a specific override by its PK (tenant-scoped). */
    @Transactional
    public void delete(Integer id) {
        RouteCalendarOverride override = findById(id);
        overrideRepository.delete(override);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void addRanges(RouteCalendarOverride override,
                           List<RouteTimeRangeRequest> requests,
                           Company company) {
        List<RouteTimeRangeRequest> sorted = requests.stream()
                .sorted((a, b) -> {
                    if (a.crossesMidnight() != b.crossesMidnight()) return a.crossesMidnight() ? 1 : -1;
                    return a.rangeStart().compareTo(b.rangeStart());
                })
                .toList();

        for (int i = 0; i < sorted.size(); i++) {
            RouteTimeRangeRequest req   = sorted.get(i);
            RouteCalendarOverrideRange range = new RouteCalendarOverrideRange(
                    req.rangeStart(), req.rangeEnd(),
                    req.durationMinutes(), i + 1, req.crossesMidnight());
            range.setOverride(override);
            range.setCompany(company);
            override.getRanges().add(range);
        }
    }
}
