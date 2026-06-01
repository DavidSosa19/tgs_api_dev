package com.example.tgs_dev.service;

import com.example.tgs_dev.controller.exception.ResourceNotFoundException;
import com.example.tgs_dev.controller.request.ScheduleTemplateRequest;
import com.example.tgs_dev.entity.Company;
import com.example.tgs_dev.entity.Route;
import com.example.tgs_dev.entity.ScheduleTemplate;
import com.example.tgs_dev.entity.ScheduleTemplateGroup;
import com.example.tgs_dev.repository.ScheduleTemplateGroupRepository;
import com.example.tgs_dev.repository.ScheduleTemplateRepository;
import com.example.tgs_dev.repository.filter.FilterRequest;
import com.example.tgs_dev.repository.specification.CommonSpecifications;
import com.example.tgs_dev.repository.specification.TenantSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Service for {@link ScheduleTemplate} entities — SCD Type-2 aware.
 *
 * <p>Route resolution is done by the caller (controller) which loads the
 * {@link Route} versions by group id and passes them in.  The service owns the
 * version lifecycle (create group + first version, close-and-open on update,
 * deactivate, reactivate).
 *
 * <p>See {@link PersonService} for the full SCD mutation pattern description.
 */
@Service
public class ScheduleTemplateService {

    private final ScheduleTemplateRepository      scheduleTemplateRepository;
    private final ScheduleTemplateGroupRepository scheduleTemplateGroupRepository;
    private final TenantService                   tenantService;

    public ScheduleTemplateService(ScheduleTemplateRepository      scheduleTemplateRepository,
                                   ScheduleTemplateGroupRepository scheduleTemplateGroupRepository,
                                   TenantService                   tenantService) {
        this.scheduleTemplateRepository      = scheduleTemplateRepository;
        this.scheduleTemplateGroupRepository = scheduleTemplateGroupRepository;
        this.tenantService                   = tenantService;
    }

    // ── Navigation (SCD-aware) ────────────────────────────────────────────────

    /**
     * Returns the current active version for the given group, routes pre-loaded.
     * Used for {@code GET /scheduleTemplate/{groupId}}.
     *
     * @throws NoSuchElementException if no active current version exists
     */
    @Transactional(readOnly = true)
    public ScheduleTemplate findByGroupId(Long groupId) {
        return scheduleTemplateRepository
                .findCurrentByGroupId(groupId, tenantService.currentCompanyId())
                .orElseThrow(() -> new NoSuchElementException("notFound.template|" + groupId));
    }

    /**
     * Returns all current versions (active + inactive) for the listing UI.
     */
    @Transactional(readOnly = true)
    public List<ScheduleTemplate> findAll() {
        return scheduleTemplateRepository
                .findAllCurrentByCompanyWithRoutes(tenantService.currentCompanyId());
    }

    // ── Internal / FK resolution ──────────────────────────────────────────────

    /**
     * Finds the current active version by entity surrogate ID — for internal FK
     * resolution.  Prefer {@link #findByGroupId(Long)} for user-facing navigation.
     */
    @Transactional(readOnly = true)
    public ScheduleTemplate findById(Integer id) {
        return scheduleTemplateRepository
                .findByIdActiveWithRoutes(id, tenantService.currentCompanyId())
                .orElseThrow(() -> new NoSuchElementException("notFound.template|" + id));
    }

    @Transactional(readOnly = true)
    public Optional<ScheduleTemplate> findByNumber(String templateNumber) {
        return scheduleTemplateRepository.findOne(
                CommonSpecifications.<ScheduleTemplate>fieldEquals("templateNumber", templateNumber)
                        .and(TenantSpecifications.belongsToCompany(tenantService.currentCompanyId()))
                        .and(TenantSpecifications.isActive()));
    }

    // ── Mutations ─────────────────────────────────────────────────────────────

    /**
     * Creates a new template group and its first version.
     *
     * @param request        the creation request
     * @param route          the resolved primary route version (must not be null)
     * @param secondaryRoute the resolved secondary route version (nullable)
     * @return the persisted first version
     */
    @Transactional
    public ScheduleTemplate create(ScheduleTemplateRequest request,
                                   Route route, Route secondaryRoute) {
        Company company = tenantService.currentCompany();

        ScheduleTemplateGroup group = scheduleTemplateGroupRepository.save(
                new ScheduleTemplateGroup(company, request.templateNumber()));

        ScheduleTemplate template = applyRequest(new ScheduleTemplate(), request, route, secondaryRoute);
        template.setCompany(company);
        template.setGroup(group);
        template.setVersionFrom(LocalDateTime.now());
        template.setVersionTo(null);
        template.setIsCurrent(true);
        return scheduleTemplateRepository.save(template);
    }

    /**
     * Updates a template by closing the current version and opening a new one.
     */
    @Transactional
    public ScheduleTemplate update(Long groupId, ScheduleTemplateRequest request,
                                   Route route, Route secondaryRoute) {
        Integer companyId = tenantService.currentCompanyId();
        ScheduleTemplate current = scheduleTemplateRepository
                .findCurrentByGroupId(groupId, companyId)
                .orElseThrow(() -> new NoSuchElementException("notFound.template|" + groupId));

        LocalDateTime now = LocalDateTime.now();
        current.setVersionTo(now);
        current.setIsCurrent(false);
        scheduleTemplateRepository.save(current);

        ScheduleTemplate next = applyRequest(new ScheduleTemplate(), request, route, secondaryRoute);
        next.setCompany(current.getCompany());
        next.setGroup(current.getGroup());
        next.setVersionFrom(now);
        next.setVersionTo(null);
        next.setIsCurrent(true);
        return scheduleTemplateRepository.save(next);
    }

    /**
     * Deactivates the current version (sets {@code active = false}).
     */
    @Transactional
    public void deactivate(Long groupId) {
        Integer companyId = tenantService.currentCompanyId();
        ScheduleTemplate current = scheduleTemplateRepository
                .findCurrentByGroupId(groupId, companyId)
                .orElseThrow(() -> new NoSuchElementException("notFound.template|" + groupId));
        scheduleTemplateRepository.softDelete(current);
    }

    /**
     * Reactivates by creating a new active version copying the last version's data.
     */
    @Transactional
    public ScheduleTemplate reactivate(Long groupId) {
        Integer companyId = tenantService.currentCompanyId();
        ScheduleTemplate last = scheduleTemplateRepository
                .findCurrentByGroupId(groupId, companyId)
                .orElseThrow(() -> new NoSuchElementException("notFound.template|" + groupId));

        LocalDateTime now = LocalDateTime.now();
        last.setVersionTo(now);
        last.setIsCurrent(false);
        scheduleTemplateRepository.save(last);

        ScheduleTemplate next = new ScheduleTemplate(
                last.getRoute(), last.getTemplateNumber(), last.getName(), last.getStartTime());
        next.setSecondaryRoute(last.getSecondaryRoute());
        next.setActive(true);
        next.setCompany(last.getCompany());
        next.setGroup(last.getGroup());
        next.setVersionFrom(now);
        next.setVersionTo(null);
        next.setIsCurrent(true);
        return scheduleTemplateRepository.save(next);
    }

    @Transactional(readOnly = true)
    public Page<ScheduleTemplate> filter(FilterRequest request) {
        return scheduleTemplateRepository.filter(
                request,
                request.toPageable(),
                TenantSpecifications.<ScheduleTemplate>belongsToCompany(tenantService.currentCompanyId())
                        .and(TenantSpecifications.isActive()));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private ScheduleTemplate applyRequest(ScheduleTemplate template,
                                          ScheduleTemplateRequest request,
                                          Route route, Route secondaryRoute) {
        template.setTemplateNumber(request.templateNumber());
        template.setName(request.name());
        template.setStartTime(request.startTime());
        template.setRoute(route);
        template.setSecondaryRoute(secondaryRoute);
        if (request.active() != null) template.setActive(request.active());
        return template;
    }
}
