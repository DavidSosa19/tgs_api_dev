package com.example.tgs_dev.service;

import com.example.tgs_dev.entity.RotationEntry;
import com.example.tgs_dev.entity.VehicleRotation;
import com.example.tgs_dev.entity.enums.ShiftDayType;
import com.example.tgs_dev.repository.VehicleRotationRepository;
import com.example.tgs_dev.repository.filter.FilterRequest;
import com.example.tgs_dev.repository.specification.CommonSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

@Service
public class VehicleRotationService {

    private final VehicleRotationRepository vehicleRotationRepository;

    public VehicleRotationService(VehicleRotationRepository vehicleRotationRepository) {
        this.vehicleRotationRepository = vehicleRotationRepository;
    }

    public VehicleRotation save(VehicleRotation vehicle){
        return vehicleRotationRepository.save(vehicle);
    }

    public VehicleRotation findById(Integer id){
        return vehicleRotationRepository.findById(id)
                .orElseThrow(()-> new NoSuchElementException("notFound.rotation|" + id));
    }

    public void delete(VehicleRotation rotation){
        vehicleRotationRepository.softDelete(rotation);
    }

    public List<VehicleRotation> findAll(){ return vehicleRotationRepository.findAll(); }

    public Page<VehicleRotation> filter(FilterRequest request) {
        return vehicleRotationRepository.filter(request, request.toPageable());
    }

    public List<RotationEntry> getRotationFromDate(ShiftDayType rotationType, LocalDate date){
        Specification<VehicleRotation> specification = Specification
                .<VehicleRotation>where(CommonSpecifications.fieldGreaterThanOrEqualTo("endDate", date))
                .and(CommonSpecifications.fieldLessThanOrEqualTo("startDate", date))
                .and(CommonSpecifications.fieldEquals("rotationType", rotationType));
        VehicleRotation rotation =  vehicleRotationRepository.findOne(specification).
                orElseThrow(()-> new NoSuchElementException("rotation.notFound|" + rotationType.name() + "|" + date));
        int offset = getBusinessDays(rotation.getStartDate(), date);
        List<RotationEntry> rotationList = rotation.getEntries();
        rotationList.sort(
                Comparator.comparing(re -> re.getScheduleTemplate().getStartTime())
        );
        return rotatePositions(rotationList,offset);
    }

    public int getBusinessDays(LocalDate startDate, LocalDate targetDate){
        if(!targetDate.isAfter(startDate)) return 0;

        int businessDays = 0;
        LocalDate date = startDate;

        while(date.isBefore(targetDate)){
            date = date.plusDays(1);
            DayOfWeek day = date.getDayOfWeek();
            if(day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY){
                businessDays++;
            }
        }
        return  businessDays;
    }

    public <T> List<T> rotatePositions(List<T> baseList, int businessDays) {
        if (baseList.isEmpty()) return baseList;

        int size = baseList.size();
        int offset = businessDays % size;

        if (offset == 0) return new ArrayList<>(baseList);

        List<T> newList = new ArrayList<>(size);
        newList.addAll(baseList.subList(size - offset, size));
        newList.addAll(baseList.subList(0, size - offset));

        return newList;
    }
}