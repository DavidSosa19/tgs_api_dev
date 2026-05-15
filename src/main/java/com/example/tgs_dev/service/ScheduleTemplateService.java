package com.example.tgs_dev.service;

import com.example.tgs_dev.entity.ScheduleTemplate;
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
        return scheduleTemplateRepository.filter(request, request.toPageable());
    }
}