package com.example.tgs_dev.service;

import com.example.tgs_dev.controller.exception.BusinessException;
import com.example.tgs_dev.controller.exception.ConflictException;
import com.example.tgs_dev.controller.exception.ResourceNotFoundException;
import com.example.tgs_dev.controller.request.ScheduleTemplateVersionRequest;
import com.example.tgs_dev.entity.Company;
import com.example.tgs_dev.entity.ScheduleTemplate;
import com.example.tgs_dev.entity.ScheduleTemplateVersion;
import com.example.tgs_dev.repository.ScheduleTemplateVersionRepository;
import com.example.tgs_dev.repository.specification.CommonSpecifications;
import com.example.tgs_dev.repository.specification.TenantSpecifications;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Manages {@link ScheduleTemplateVersion} records for the current tenant.
 *
 * <p>Mirrors the structure of {@link RouteOperationalPeriodService}: the same
 * non-overlap invariant is enforced, and {@link #findActiveForDate} returns
 * {@code empty()} when no version is active so callers fall back to the
 * template's own {@code startTime} field.
 *
 * @see RouteOperationalPeriodService for the full design rationale
 */
@Service
public class ScheduleTemplateVersionService {

    private final ScheduleTemplateVersionRepository repository;
    private final ScheduleTemplateService            templateService;
    private final TenantService                      tenantService;

    public ScheduleTemplateVersionService(ScheduleTemplateVersionRepository repository,
                                           ScheduleTemplateService templateService,
                                           TenantService tenantService) {
        this.repository      = repository;
        this.templateService = templateService;
        this.tenantService   = tenantService;
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    /**
     * Returns all active versions for the given template (tenant-scoped),
     * ordered by {@code effectiveFrom} ascending.
     *
     * @param templateId the template's primary key
     * @return list of versions, empty if none exist
     */
    public List<ScheduleTemplateVersion> findAllByTemplate(Integer templateId) {
        ScheduleTemplate template = loadTemplateOrThrow(templateId);
        return repository.findAllByTemplateAndCompany(template, tenantService.currentCompanyId());
    }

    /**
     * Returns one version by ID, validated against the current tenant and
     * the expected parent template.
     *
     * @param templateId the parent template's ID (ownership check)
     * @param versionId  the version's ID
     * @return the matching version
     * @throws ResourceNotFoundException if not found or cross-tenant
     */
    public ScheduleTemplateVersion findById(Integer templateId, Integer versionId) {
        return loadVersionOrThrow(templateId, versionId);
    }

    /**
     * Returns the version active on {@code date} for the given template and
     * tenant, or {@code empty()} when no version covers that date.
     *
     * <p>Called by {@link ScheduleService} to resolve the effective
     * {@code startTime} before generating departure schedules.
     *
     * @param template  the schedule template being scheduled
     * @param companyId the current tenant's ID
     * @param date      the operation's service date
     * @return the active version, or empty to fall back to template's startTime
     */
    public Optional<ScheduleTemplateVersion> findActiveForDate(ScheduleTemplate template,
                                                                Integer companyId,
                                                                LocalDate date) {
        return repository.findActiveForDate(template, companyId, date);
    }

    // ── Mutations ─────────────────────────────────────────────────────────────

    /**
     * Creates a new version for the given template.
     *
     * @param templateId the parent template's ID
     * @param request    the creation request (validated by the controller)
     * @return the persisted version
     * @throws BusinessException if {@code effectiveTo < effectiveFrom}
     * @throws ConflictException if the date range overlaps an existing version
     */
    @Transactional
    public ScheduleTemplateVersion create(Integer templateId,
                                           ScheduleTemplateVersionRequest request) {
        ScheduleTemplate template = loadTemplateOrThrow(templateId);
        Company          company  = tenantService.currentCompany();

        assertValidDateRange(request.effectiveFrom(), request.effectiveTo());
        assertNoOverlap(template, request.effectiveFrom(), request.effectiveTo(), -1);

        ScheduleTemplateVersion version = new ScheduleTemplateVersion(
                template,
                company,
                request.label(),
                request.startTime(),
                request.effectiveFrom(),
                request.effectiveTo()
        );
        return repository.save(version);
    }

    /**
     * Updates an existing version.
     *
     * @param templateId the parent template's ID (ownership check)
     * @param versionId  the version to update
     * @param request    the update request
     * @return the updated version
     * @throws BusinessException if {@code effectiveTo < effectiveFrom}
     * @throws ConflictException if the new date range overlaps another version
     */
    @Transactional
    public ScheduleTemplateVersion update(Integer templateId, Integer versionId,
                                           ScheduleTemplateVersionRequest request) {
        ScheduleTemplateVersion version = loadVersionOrThrow(templateId, versionId);

        assertValidDateRange(request.effectiveFrom(), request.effectiveTo());
        assertNoOverlap(version.getTemplate(), request.effectiveFrom(),
                request.effectiveTo(), versionId);

        version.setLabel(request.label());
        version.setStartTime(request.startTime());
        version.setEffectiveFrom(request.effectiveFrom());
        version.setEffectiveTo(request.effectiveTo());

        return repository.save(version);
    }

    /**
     * Soft-deletes a version.
     *
     * @param templateId the parent template's ID (ownership check)
     * @param versionId  the version to delete
     */
    @Transactional
    public void delete(Integer templateId, Integer versionId) {
        ScheduleTemplateVersion version = loadVersionOrThrow(templateId, versionId);
        repository.softDelete(version);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private ScheduleTemplate loadTemplateOrThrow(Integer templateId) {
        return templateService.findById(templateId);   // already tenant-scoped + 404
    }

    private ScheduleTemplateVersion loadVersionOrThrow(Integer templateId, Integer versionId) {
        Integer companyId = tenantService.currentCompanyId();
        return repository.findOne(
                Specification.<ScheduleTemplateVersion>where(
                        CommonSpecifications.fieldEquals("id", versionId))
                        .and(TenantSpecifications.belongsToCompany(companyId))
        ).filter(v -> v.getTemplate().getId().equals(templateId))
         .orElseThrow(() -> new ResourceNotFoundException(
                 "notFound.scheduleTemplateVersion|" + versionId));
    }

    private void assertValidDateRange(LocalDate effectiveFrom, LocalDate effectiveTo) {
        if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw new BusinessException("validation.scheduleTemplateVersion.invalidDateRange");
        }
    }

    private void assertNoOverlap(ScheduleTemplate template, LocalDate effectiveFrom,
                                  LocalDate effectiveTo, Integer excludeId) {
        List<ScheduleTemplateVersion> overlapping = repository.findOverlapping(
                template, tenantService.currentCompanyId(),
                effectiveFrom, effectiveTo, excludeId);
        if (!overlapping.isEmpty()) {
            throw new ConflictException("conflict.scheduleTemplateVersion.overlap");
        }
    }
}
