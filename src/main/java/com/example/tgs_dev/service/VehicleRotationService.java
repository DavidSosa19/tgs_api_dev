package com.example.tgs_dev.service;

import com.example.tgs_dev.entity.RotationEntry;
import com.example.tgs_dev.entity.Vehicle;
import com.example.tgs_dev.entity.VehicleRotation;
import com.example.tgs_dev.entity.enums.ShiftDayType;
import com.example.tgs_dev.repository.VehicleRotationRepository;
import com.example.tgs_dev.repository.filter.FilterRequest;
import com.example.tgs_dev.repository.specification.CommonSpecifications;
import com.example.tgs_dev.repository.specification.TenantSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class VehicleRotationService {

    private final VehicleRotationRepository vehicleRotationRepository;
    private final TenantService             tenantService;

    public VehicleRotationService(VehicleRotationRepository vehicleRotationRepository,
                                  TenantService tenantService) {
        this.vehicleRotationRepository = vehicleRotationRepository;
        this.tenantService             = tenantService;
    }

    public VehicleRotation save(VehicleRotation rotation) {
        rotation.setCompany(tenantService.currentCompany());
        return vehicleRotationRepository.save(rotation);
    }

    public VehicleRotation findById(Integer id) {
        Integer companyId = tenantService.currentCompanyId();
        return vehicleRotationRepository.findOne(
                Specification.<VehicleRotation>where(CommonSpecifications.fieldEquals("id", id))
                        .and(TenantSpecifications.belongsToCompany(companyId))
        ).orElseThrow(() -> new NoSuchElementException("notFound.rotation|" + id));
    }

    public void delete(VehicleRotation rotation) {
        vehicleRotationRepository.softDelete(rotation);
    }

    public List<VehicleRotation> findAll() {
        return vehicleRotationRepository.findAll(
                TenantSpecifications.belongsToCompany(tenantService.currentCompanyId()));
    }

    public Page<VehicleRotation> filter(FilterRequest request) {
        return vehicleRotationRepository.filter(
                request,
                request.toPageable(),
                TenantSpecifications.belongsToCompany(tenantService.currentCompanyId()));
    }

    public List<RotationEntry> getRotationFromDate(ShiftDayType rotationType, LocalDate date) {
        Integer companyId = tenantService.currentCompanyId();

        VehicleRotation rotation = vehicleRotationRepository
                .findByDateAndTypeEager(date, rotationType, companyId)
                .orElseThrow(() -> new NoSuchElementException(
                        "rotation.notFound|" + rotationType.name() + "|" + date));

        int offset = getBusinessDays(rotation.getStartDate(), date);

        // Sort entries by their template's start time to establish the stable
        // slot ordering.  Slot 0 = earliest departure, slot N-1 = latest.
        // We copy the list to avoid mutating the Hibernate collection.
        List<RotationEntry> sortedSlots = new ArrayList<>(rotation.getEntries());
        sortedSlots.sort(Comparator.comparing(e -> e.getScheduleTemplate().getStartTime()));

        // Rotate only the vehicles, keeping each template pinned to its slot.
        //
        // Rotating the full RotationEntry objects (old approach) moved the
        // template along with its vehicle, breaking departure-time ordering:
        // the vehicle that reached position 0 still carried its own late-start
        // template instead of the slot-0 (earliest) template.
        //
        // Correct model: templates are fixed by slot; vehicles cycle through
        // slots each business day.
        List<Vehicle> vehicles = sortedSlots.stream()
                .map(RotationEntry::getVehicle)
                .collect(Collectors.toCollection(ArrayList::new));
        List<Vehicle> rotatedVehicles = rotatePositions(vehicles, offset);

        // Build transient (non-persisted) RotationEntry views: each view
        // carries the slot's fixed template but the day's rotated vehicle.
        // Hibernate will not dirty-check or cascade-persist these because
        // they have no @Id assigned.
        List<RotationEntry> result = new ArrayList<>(sortedSlots.size());
        for (int i = 0; i < sortedSlots.size(); i++) {
            RotationEntry slot = sortedSlots.get(i);
            RotationEntry view = new RotationEntry();
            view.setScheduleTemplate(slot.getScheduleTemplate());
            view.setVehicle(rotatedVehicles.get(i));
            view.setCompany(slot.getCompany());
            view.setVehicleRotation(slot.getVehicleRotation());
            view.setListPosition(i);
            result.add(view);
        }
        return result;
    }

    public int getBusinessDays(LocalDate startDate, LocalDate targetDate) {
        if (!targetDate.isAfter(startDate)) return 0;

        int businessDays = 0;
        LocalDate date = startDate;

        while (date.isBefore(targetDate)) {
            date = date.plusDays(1);
            DayOfWeek day = date.getDayOfWeek();
            if (day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY) {
                businessDays++;
            }
        }
        return businessDays;
    }

    public <T> List<T> rotatePositions(List<T> baseList, int businessDays) {
        if (baseList.isEmpty()) return baseList;

        int size   = baseList.size();
        int offset = businessDays % size;

        if (offset == 0) return new ArrayList<>(baseList);

        List<T> newList = new ArrayList<>(size);
        newList.addAll(baseList.subList(size - offset, size));
        newList.addAll(baseList.subList(0, size - offset));

        return newList;
    }
}
