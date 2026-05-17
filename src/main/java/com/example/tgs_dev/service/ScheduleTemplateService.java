package com.example.tgs_dev.service;

import com.example.tgs_dev.entity.ScheduleTemplate;
import com.example.tgs_dev.repository.ScheduleTemplateRepository;
import com.example.tgs_dev.repository.filter.FilterRequest;
import com.example.tgs_dev.repository.specification.CommonSpecifications;
import com.example.tgs_dev.repository.specification.TenantSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

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

    public List<ScheduleTemplate> findAll() {
        return scheduleTemplateRepository.findAll(
                TenantSpecifications.belongsToCompany(tenantService.currentCompanyId()));
    }

    public ScheduleTemplate save(ScheduleTemplate scheduleTemplate) {
        scheduleTemplate.setCompany(tenantService.currentCompany());
        return scheduleTemplateRepository.save(scheduleTemplate);
    }

    public ScheduleTemplate findById(Integer id) {
        return scheduleTemplateRepository.findOne(
                Specification.<ScheduleTemplate>where(CommonSpecifications.fieldEquals("id", id))
                             .and(TenantSpecifications.belongsToCompany(tenantService.currentCompanyId()))
        ).orElseThrow(() -> new NoSuchElementException("notFound.template|" + id));
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

    public Page<ScheduleTemplate> filter(FilterRequest request) {
        return scheduleTemplateRepository.filter(
                request,
                request.toPageable(),
                TenantSpecifications.belongsToCompany(tenantService.currentCompanyId()));
    }
}
