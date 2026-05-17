package com.example.tgs_dev.service;

import com.example.tgs_dev.entity.Vehicle;
import com.example.tgs_dev.repository.VehicleRepository;
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
public class VehicleService {

    private final VehicleRepository vehicleRepository;
    private final TenantService     tenantService;

    public VehicleService(VehicleRepository vehicleRepository, TenantService tenantService) {
        this.vehicleRepository = vehicleRepository;
        this.tenantService     = tenantService;
    }

    public Vehicle save(Vehicle vehicle) {
        vehicle.setCompany(tenantService.currentCompany());
        return vehicleRepository.save(vehicle);
    }

    public Vehicle findById(Integer id) {
        Integer companyId = tenantService.currentCompanyId();
        return vehicleRepository.findOne(
                Specification.<Vehicle>where(CommonSpecifications.fieldEquals("id", id))
                        .and(TenantSpecifications.belongsToCompany(companyId))
        ).orElseThrow(() -> new NoSuchElementException("notFound.vehicle|" + id));
    }

    public List<Vehicle> findAll() {
        return vehicleRepository.findAll(
                TenantSpecifications.belongsToCompany(tenantService.currentCompanyId()));
    }

    public void delete(Vehicle vehicle) {
        vehicleRepository.softDelete(vehicle);
    }

    public Optional<Vehicle> findByNumber(String vehicleNumber) {
        return vehicleRepository.findOne(
                CommonSpecifications.<Vehicle>fieldEquals("vehicleNumber", vehicleNumber)
                        .and(TenantSpecifications.belongsToCompany(tenantService.currentCompanyId())));
    }

    public Page<Vehicle> filter(FilterRequest request) {
        return vehicleRepository.filter(
                request,
                request.toPageable(),
                TenantSpecifications.belongsToCompany(tenantService.currentCompanyId()));
    }
}
