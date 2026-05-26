package com.example.tgs_dev.service;

import com.example.tgs_dev.entity.Company;
import com.example.tgs_dev.entity.RotationEntry;
import com.example.tgs_dev.entity.VehicleRotation;
import com.example.tgs_dev.repository.RotationEntryRepository;
import com.example.tgs_dev.repository.specification.CommonSpecifications;
import com.example.tgs_dev.repository.specification.TenantSpecifications;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class RotationEntryService {

    private final RotationEntryRepository rotationEntryRepository;
    private final TenantService           tenantService;

    public RotationEntryService(RotationEntryRepository rotationEntryRepository,
                                TenantService tenantService) {
        this.rotationEntryRepository = rotationEntryRepository;
        this.tenantService           = tenantService;
    }

    public RotationEntry save(RotationEntry rotationEntry){
        if (rotationEntry.getCompany() == null) {
            rotationEntry.setCompany(tenantService.currentCompany());
        }
        return rotationEntryRepository.save(rotationEntry);
    }

    public List<RotationEntry> saveAll(VehicleRotation rotation, List<RotationEntry> entries){
        Company company = tenantService.currentCompany();
        for (RotationEntry entry : entries){
            entry.setVehicleRotation(rotation);
            if (entry.getCompany() == null) {
                entry.setCompany(company);
            }
        }
        return rotationEntryRepository.saveAll(entries);
    }

    public Optional<RotationEntry> findById(Integer id){
        Integer companyId = tenantService.currentCompanyId();
        return rotationEntryRepository.findOne(
                Specification.<RotationEntry>where(CommonSpecifications.fieldEquals("id", id))
                        .and(TenantSpecifications.belongsToCompany(companyId)));
    }

    public List<RotationEntry> findAll(){
        return rotationEntryRepository.findAll(
                TenantSpecifications.belongsToCompany(tenantService.currentCompanyId()));
    }

    public void delete(RotationEntry rotationEntry){
        rotationEntryRepository.delete(rotationEntry);
    }

    public void deleteAll(List<RotationEntry> entries){
        rotationEntryRepository.deleteAll(entries);
    }

    public List<RotationEntry> findByRotation(VehicleRotation vehicleRotation){
        return rotationEntryRepository.findByRotationEager(vehicleRotation);
    }
}
