package com.example.tgs_dev.service;

import com.example.tgs_dev.controller.exception.BusinessException;
import com.example.tgs_dev.controller.exception.ResourceNotFoundException;
import com.example.tgs_dev.controller.request.RouteTimeRangeRequest;
import com.example.tgs_dev.controller.request.SeasonalPatternRequest;
import com.example.tgs_dev.entity.Company;
import com.example.tgs_dev.entity.Route;
import com.example.tgs_dev.entity.SeasonalPattern;
import com.example.tgs_dev.entity.SeasonalPatternRange;
import com.example.tgs_dev.repository.SeasonalPatternRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Manages {@link SeasonalPattern} instances for a tenant.
 */
@Service
public class SeasonalPatternService {

    private final SeasonalPatternRepository patternRepository;
    private final TenantService             tenantService;
    private final RouteService              routeService;

    public SeasonalPatternService(SeasonalPatternRepository patternRepository,
                                  TenantService tenantService,
                                  RouteService routeService) {
        this.patternRepository = patternRepository;
        this.tenantService     = tenantService;
        this.routeService      = routeService;
    }

    // ── Resolver-facing API ───────────────────────────────────────────────────

    /**
     * Returns the first (lowest id) active seasonal pattern covering the given
     * date for the specified route, scoped to the current tenant.
     */
    public Optional<SeasonalPattern> findActivePatternForDate(Route route,
                                                              java.time.LocalDate date) {
        Integer companyId = tenantService.currentCompanyId();
        return patternRepository.findFirstActiveForDate(route.getId(), companyId, date);
    }

    // ── Management API ────────────────────────────────────────────────────────

    /** Returns all patterns for a route (management listing). */
    public List<SeasonalPattern> findAllByRoute(Integer routeId) {
        Route route = routeService.findById(routeId);
        return patternRepository.findAllByRouteAndCompany(route.getId(),
                tenantService.currentCompanyId());
    }

    /** Finds a single pattern by PK, tenant-scoped. */
    public SeasonalPattern findById(Integer id) {
        return patternRepository.findOne(
                (root, query, cb) -> cb.and(
                        cb.equal(root.get("id"), id),
                        cb.equal(root.get("company").get("id"), tenantService.currentCompanyId()))
        ).orElseThrow(() -> new ResourceNotFoundException("notFound.seasonalPattern|" + id));
    }

    /** Creates a new seasonal pattern for the given route. */
    @Transactional
    public SeasonalPattern save(Integer routeId, SeasonalPatternRequest request) {
        Company company = tenantService.currentCompany();
        Route   route   = routeService.findById(routeId);

        validateRequest(request);

        SeasonalPattern pattern = new SeasonalPattern(
                route, company,
                request.name(),
                request.seasonFrom(),
                request.seasonTo(),
                request.useTimeRanges(),
                request.baseDuration());

        if (Boolean.TRUE.equals(request.useTimeRanges()) && request.ranges() != null) {
            addRanges(pattern, request.ranges(), company);
        }

        return patternRepository.save(pattern);
    }

    /** Replaces an existing seasonal pattern (full update). */
    @Transactional
    public SeasonalPattern update(Integer id, SeasonalPatternRequest request) {
        SeasonalPattern pattern = findById(id);
        Company         company = tenantService.currentCompany();

        validateRequest(request);

        pattern.setName(request.name());
        pattern.setSeasonFrom(request.seasonFrom());
        pattern.setSeasonTo(request.seasonTo());
        pattern.setUseTimeRanges(request.useTimeRanges());
        pattern.setBaseDuration(request.baseDuration());
        pattern.getRanges().clear();

        if (Boolean.TRUE.equals(request.useTimeRanges()) && request.ranges() != null) {
            addRanges(pattern, request.ranges(), company);
        }

        return patternRepository.save(pattern);
    }

    /** Deletes a seasonal pattern by PK (tenant-scoped). */
    @Transactional
    public void delete(Integer id) {
        SeasonalPattern pattern = findById(id);
        patternRepository.delete(pattern);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void validateRequest(SeasonalPatternRequest request) {
        if (request.seasonTo().isBefore(request.seasonFrom())) {
            throw new BusinessException("seasonalPattern.dateOrderInvalid");
        }
        if (Boolean.TRUE.equals(request.useTimeRanges())) {
            TimeRangeValidator.validate(request.ranges(), "seasonalPattern");
        }
    }

    private void addRanges(SeasonalPattern pattern,
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
            SeasonalPatternRange  range = new SeasonalPatternRange(
                    req.rangeStart(), req.rangeEnd(),
                    req.durationMinutes(), i + 1, req.crossesMidnight());
            range.setPattern(pattern);
            range.setCompany(company);
            pattern.getRanges().add(range);
        }
    }
}
