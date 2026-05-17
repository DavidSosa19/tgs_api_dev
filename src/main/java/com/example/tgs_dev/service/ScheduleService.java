package com.example.tgs_dev.service;

import com.example.tgs_dev.entity.Company;
import com.example.tgs_dev.entity.Route;
import com.example.tgs_dev.entity.Schedule;
import com.example.tgs_dev.entity.ScheduleTemplate;
import com.example.tgs_dev.entity.VehicleAssignment;
import com.example.tgs_dev.repository.ScheduleRepository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final TenantService      tenantService;

    public ScheduleService(ScheduleRepository scheduleRepository, TenantService tenantService) {
        this.scheduleRepository = scheduleRepository;
        this.tenantService      = tenantService;
    }

    public List<Schedule> findAll(){ return scheduleRepository.findAll(); }

    public Schedule save(Schedule schedule){
        return scheduleRepository.save(schedule);
    }

    public Optional<Schedule> findById(Integer id){
        return scheduleRepository.findById(id);
    }

    public void delete(Schedule schedule){
        scheduleRepository.delete(schedule);
    }

    public List<Schedule> findAllByAssignment(List<Integer> ids) {
        Specification<Schedule> specification = (root, query, cb) ->
                root.join("vehicleAssignment").get("id").in(ids);
        return scheduleRepository.findAll(specification);
    }

    public List<Schedule> findAllById(List<Integer> ids){
        return scheduleRepository.findAllById(ids);
    }

    public List<Schedule> saveAll(List<Schedule> schedules) {
        return scheduleRepository.saveAll(schedules);
    }
    public void calculateVehicleSchedules(List<VehicleAssignment> assignments){
        Company company = tenantService.currentCompany();
        List<Schedule> schedules = new ArrayList<>();
        for(VehicleAssignment assignment: assignments){
            ScheduleTemplate template = assignment.getScheduleTemplate();
            Route currentRoute = template.getRoute();
            LocalTime startTime = template.getStartTime();
            Integer durationMinutes = currentRoute.getBaseDuration();
            int baseCycleCount = currentRoute.getCycleCount();
            LocalTime time = startTime;
            for (int i = 0; i < baseCycleCount; i++) {
                Schedule newSchedule = new Schedule(assignment, i + 1, time);
                newSchedule.setCompany(company);
                schedules.add(newSchedule);
                time = time.plusMinutes(durationMinutes);
            }
        }
        scheduleRepository.saveAll(schedules);
    }
}
