package com.example.tgs_dev.service;

import com.example.tgs_dev.entity.ScheduleTemplate;
import com.example.tgs_dev.entity.Vehicle;
import com.example.tgs_dev.repository.ScheduleTemplateRepository;
import com.example.tgs_dev.repository.filter.FilterRequest;
import com.example.tgs_dev.repository.specification.CommonSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
public class ScheduleTemplateService {

    private final ScheduleTemplateRepository scheduleTemplateRepository;

    public ScheduleTemplateService(ScheduleTemplateRepository scheduleTemplateRepository ) {
        this.scheduleTemplateRepository = scheduleTemplateRepository;
    }

    public List<ScheduleTemplate> findAll(){ return scheduleTemplateRepository.findAll(); }

    public ScheduleTemplate save(ScheduleTemplate scheduleTemplate){
        return scheduleTemplateRepository.save(scheduleTemplate);
    }

    public ScheduleTemplate findById(Integer id){
        return scheduleTemplateRepository.findById(id)
                .orElseThrow(()-> new NoSuchElementException("notFound.template|" + id));
    }

    public void delete(ScheduleTemplate scheduleTemplate){
        scheduleTemplateRepository.softDelete(scheduleTemplate);
    }

    public Optional<ScheduleTemplate> findByNumber(String templateNumber){
        return scheduleTemplateRepository.findOne(CommonSpecifications.fieldEquals("templateNumber", templateNumber));
    }

    public Page<ScheduleTemplate> filter(FilterRequest request) {
        return scheduleTemplateRepository.filter(request);
    }
}


//FilterRequest request = new FilterRequest();
//    request.setPage(page);
//    request.setSize(size);
//    request.setSortBy(sortBy);
//    request.setSortDirection(sortDirection);
//
//List<FilterCriteria> filters = new ArrayList<>();
//
//    if (status != null) {
//        filters.add(new FilterCriteria("status", FilterOperator.EQUALS, status, null));
//        }
//        if (destination != null) {
//        filters.add(new FilterCriteria("destination", FilterOperator.LIKE, destination, null));
//        }
//        if (minDelay != null) {
//        filters.add(new FilterCriteria("delayMinutes", FilterOperator.GREATER_THAN, minDelay, null));
//        }
//
//        request.setFilters(filters);
//    return ResponseEntity.ok(service.filter(request));