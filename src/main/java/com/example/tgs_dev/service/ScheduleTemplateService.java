package com.example.tgs_dev.service;

import com.example.tgs_dev.entity.ScheduleTemplate;
import com.example.tgs_dev.repository.ScheduleTemplateRepository;
import com.example.tgs_dev.repository.filter.FilterRequest;
import com.example.tgs_dev.repository.specification.CommonSpecifications;
import com.example.tgs_dev.repository.specification.TenantSpecifications;
import org.hibernate.Hibernate;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
public class ScheduleTemplateService {

    private final ScheduleTemplateRepository scheduleTemplateRepository;
    private final TenantService              tenantService;

    public ScheduleTemplateService(ScheduleTemplateRepository scheduleTemplateRepository,
                                   TenantService              tenantService) {
        this.scheduleTemplateRepository = scheduleTemplateRepository;
        this.tenantService              = tenantService;
    }

    /**
     * Returns all active templates for the current tenant with {@code route} and
     * {@code secondaryRoute} eagerly fetched in a single JOIN FETCH query.
     *
     * <p>{@code @Transactional(readOnly = true)} + JOIN FETCH prevents
     * {@code LazyInitializationException} when the mapper accesses
     * {@code route.getRouteNumber()} after the session would otherwise close
     * (OSIV is disabled: {@code spring.jpa.open-in-view=false}).
     */
    @Transactional(readOnly = true)
    public List<ScheduleTemplate> findAll() {
        return scheduleTemplateRepository.findAllByCompanyWithRoutes(tenantService.currentCompanyId());
    }

    public ScheduleTemplate save(ScheduleTemplate scheduleTemplate) {
        scheduleTemplate.setCompany(tenantService.currentCompany());
        return scheduleTemplateRepository.save(scheduleTemplate);
    }

    /**
     * Fetches a single template by ID with both route associations pre-loaded.
     * Using a dedicated JOIN FETCH query avoids {@code LazyInitializationException}
     * when the mapper accesses route fields after the session closes.
     */
    @Transactional(readOnly = true)
    public ScheduleTemplate findById(Integer id) {
        return scheduleTemplateRepository
                .findByIdWithRoutes(id, tenantService.currentCompanyId())
                .orElseThrow(() -> new NoSuchElementException("notFound.template|" + id));
    }

    public void delete(ScheduleTemplate scheduleTemplate) {
        scheduleTemplateRepository.softDelete(scheduleTemplate);
    }

    public Optional<ScheduleTemplate> findByNumber(String templateNumber) {
        return scheduleTemplateRepository.findOne(
                CommonSpecifications.<ScheduleTemplate>fieldEquals("templateNumber", templateNumber)
                                    .and(TenantSpecifications.belongsToCompany(tenantService.currentCompanyId()))
        );
    }

    /**
     * Returns a filtered page of templates with {@code route} and
     * {@code secondaryRoute} initialized within the transaction so the mapper
     * can access them safely after the session closes (OSIV disabled).
     *
     * <p>JOIN FETCH is incompatible with {@code Page} (breaks the count query),
     * so both associations are instead force-initialized via
     * {@link Hibernate#initialize} while the session is still open.
     */
    @Transactional(readOnly = true)
    public Page<ScheduleTemplate> filter(FilterRequest request) {
        Page<ScheduleTemplate> page = scheduleTemplateRepository.filter(
                request,
                request.toPageable(),
                TenantSpecifications.belongsToCompany(tenantService.currentCompanyId()));
        page.forEach(t -> {
            Hibernate.initialize(t.getRoute());
            Hibernate.initialize(t.getSecondaryRoute());
        });
        return page;
    }
}
