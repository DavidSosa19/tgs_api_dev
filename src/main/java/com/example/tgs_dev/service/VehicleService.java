package com.example.tgs_dev.service;

import com.example.tgs_dev.controller.request.VehicleRequest;
import com.example.tgs_dev.entity.Company;
import com.example.tgs_dev.entity.Person;
import com.example.tgs_dev.entity.Vehicle;
import com.example.tgs_dev.entity.VehicleGroup;
import com.example.tgs_dev.repository.PersonRepository;
import com.example.tgs_dev.repository.VehicleGroupRepository;
import com.example.tgs_dev.repository.VehicleRepository;
import com.example.tgs_dev.repository.filter.FilterRequest;
import com.example.tgs_dev.repository.specification.CommonSpecifications;
import com.example.tgs_dev.repository.specification.TenantSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Service for {@link Vehicle} entities — SCD Type-2 aware.
 *
 * <p>See {@link PersonService} for the full SCD mutation pattern description.
 */
@Service
public class VehicleService {

    private final VehicleRepository      vehicleRepository;
    private final VehicleGroupRepository vehicleGroupRepository;
    private final PersonService          personService;
    private final TenantService          tenantService;

    public VehicleService(VehicleRepository      vehicleRepository,
                          VehicleGroupRepository vehicleGroupRepository,
                          PersonService          personService,
                          TenantService          tenantService) {
        this.vehicleRepository      = vehicleRepository;
        this.vehicleGroupRepository = vehicleGroupRepository;
        this.personService          = personService;
        this.tenantService          = tenantService;
    }

    // ── Navigation (SCD-aware) ────────────────────────────────────────────────

    /**
     * Returns the current  version for the given group.
     * Used for {@code GET /vehicles/{groupId}}.
     *
     * @throws NoSuchElementException if no current version exists
     */
    @Transactional(readOnly = true)
    public Vehicle findByGroupId(Long groupId) {
        return vehicleRepository
                .findCurrentByGroupId(groupId, tenantService.currentCompanyId())
                .orElseThrow(() -> new NoSuchElementException("notFound.vehicle|" + groupId));
    }

    /**
     * Returns all current versions (active + inactive) for the company listing.
     */
    @Transactional(readOnly = true)
    public List<Vehicle> findAll() {
        return vehicleRepository.findAllCurrentByCompany(tenantService.currentCompanyId());
    }

    // ── Internal / FK resolution ──────────────────────────────────────────────

    /**
     * Finds by entity surrogate ID — for internal FK resolution only.
     * Prefer {@link #findByGroupId(Long)} for user-facing navigation.
     */
    @Transactional(readOnly = true)
    public Vehicle findById(Integer id) {
        return vehicleRepository.findOne(
                Specification.<Vehicle>where(CommonSpecifications.fieldEquals("id", id))
                        .and(TenantSpecifications.belongsToCompany(tenantService.currentCompanyId()))
                        .and(TenantSpecifications.isActive())
        ).orElseThrow(() -> new NoSuchElementException("notFound.vehicle|" + id));
    }

    @Transactional(readOnly = true)
    public Optional<Vehicle> findByNumber(String vehicleNumber) {
        return vehicleRepository.findOne(
                CommonSpecifications.<Vehicle>fieldEquals("vehicleNumber", vehicleNumber)
                        .and(TenantSpecifications.belongsToCompany(tenantService.currentCompanyId()))
                        .and(TenantSpecifications.isActive()));
    }

    // ── Mutations ─────────────────────────────────────────────────────────────

    /**
     * Creates a new vehicle group and its first version.
     */
    @Transactional
    public Vehicle create(VehicleRequest request) {
        Company company = tenantService.currentCompany();

        VehicleGroup group = vehicleGroupRepository.save(
                new VehicleGroup(company, request.vehicleNumber()));

        Vehicle vehicle = buildFromRequest(request, new Vehicle());
        vehicle.setCompany(company);
        vehicle.setGroup(group);
        vehicle.setVersionFrom(LocalDateTime.now());
        vehicle.setVersionTo(null);
        vehicle.setIsCurrent(true);
        return vehicleRepository.save(vehicle);
    }

    /**
     * Updates a vehicle by closing the current version and opening a new one.
     */
    @Transactional
    public Vehicle update(Long groupId, VehicleRequest request) {
        Integer companyId = tenantService.currentCompanyId();
        Vehicle current = vehicleRepository
                .findCurrentByGroupId(groupId, companyId)
                .orElseThrow(() -> new NoSuchElementException("notFound.vehicle|" + groupId));

        LocalDateTime now = LocalDateTime.now();
        current.setVersionTo(now);
        current.setIsCurrent(false);
        vehicleRepository.save(current);

        Vehicle next = buildFromRequest(request, new Vehicle());
        next.setCompany(current.getCompany());
        next.setGroup(current.getGroup());
        next.setVersionFrom(now);
        next.setVersionTo(null);
        next.setIsCurrent(true);
        return vehicleRepository.save(next);
    }

    /**
     * Deactivates the current version (sets {@code active = false}).
     */
    @Transactional
    public void deactivate(Long groupId) {
        Integer companyId = tenantService.currentCompanyId();
        Vehicle current = vehicleRepository
                .findCurrentByGroupId(groupId, companyId)
                .orElseThrow(() -> new NoSuchElementException("notFound.vehicle|" + groupId));
        vehicleRepository.softDelete(current);
    }

    /**
     * Reactivates by creating a new active version that copies the last version's data.
     */
    @Transactional
    public Vehicle reactivate(Long groupId) {
        Integer companyId = tenantService.currentCompanyId();
        Vehicle last = vehicleRepository
                .findCurrentByGroupId(groupId, companyId)
                .orElseThrow(() -> new NoSuchElementException("notFound.vehicle|" + groupId));

        LocalDateTime now = LocalDateTime.now();
        last.setVersionTo(now);
        last.setIsCurrent(false);
        vehicleRepository.save(last);

        Vehicle next = new Vehicle(last.getVehicleNumber(), last.getOwner());
        next.setLicensePlate(last.getLicensePlate());
        next.setActive(true);
        next.setCompany(last.getCompany());
        next.setGroup(last.getGroup());
        next.setVersionFrom(now);
        next.setVersionTo(null);
        next.setIsCurrent(true);
        return vehicleRepository.save(next);
    }

    @Transactional(readOnly = true)
    public Page<Vehicle> filter(FilterRequest request) {
        return vehicleRepository.filter(
                request,
                request.toPageable(),
                TenantSpecifications.<Vehicle>belongsToCompany(tenantService.currentCompanyId())
                        .and(TenantSpecifications.isActive()));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Vehicle buildFromRequest(VehicleRequest request, Vehicle vehicle) {
        vehicle.setVehicleNumber(request.vehicleNumber());
        vehicle.setLicensePlate(request.licensePlate());
        Person owner = request.ownerId() != null
                ? personService.findByGroupId(request.ownerId())
                : null;
        vehicle.setOwner(owner);
        return vehicle;
    }
}
