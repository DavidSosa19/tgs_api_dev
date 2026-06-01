package com.example.tgs_dev.service;

import com.example.tgs_dev.controller.exception.BusinessException;
import com.example.tgs_dev.controller.exception.ResourceNotFoundException;
import com.example.tgs_dev.controller.request.PersonRequest;
import com.example.tgs_dev.entity.Company;
import com.example.tgs_dev.entity.Person;
import com.example.tgs_dev.entity.PersonGroup;
import com.example.tgs_dev.repository.PersonGroupRepository;
import com.example.tgs_dev.repository.PersonRepository;
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
 * Service for {@link Person} entities — SCD Type-2 aware.
 *
 * <h3>Navigation vs FK resolution</h3>
 * <ul>
 *   <li>{@link #findByGroupId(Long)} — user-facing navigation; resolves the
 *       <em>current active</em> version for a {@code person_group.id}.</li>
 *   <li>{@link #findById(Integer)} — internal FK resolution; finds by the
 *       entity surrogate ID (used by admin flows, etc.).</li>
 * </ul>
 *
 * <h3>Mutation pattern</h3>
 * {@link #create(PersonRequest)} creates a new group and the first version.
 * {@link #update(Long, PersonRequest)} closes the current version and opens a
 * new one — preserving historical FK references in operational records.
 * {@link #deactivate(Long)} marks the current version inactive without creating
 * a new one.  {@link #reactivate(Long)} creates a new active version from the
 * last version's data.
 */
@Service
public class PersonService {

    private final PersonRepository      personRepository;
    private final PersonGroupRepository personGroupRepository;
    private final VehicleRepository     vehicleRepository;
    private final TenantService         tenantService;

    public PersonService(PersonRepository      personRepository,
                         PersonGroupRepository personGroupRepository,
                         VehicleRepository     vehicleRepository,
                         TenantService         tenantService) {
        this.personRepository      = personRepository;
        this.personGroupRepository = personGroupRepository;
        this.vehicleRepository     = vehicleRepository;
        this.tenantService         = tenantService;
    }

    // ── Navigation (SCD-aware) ────────────────────────────────────────────────

    /**
     * Returns the current active version for the given group.
     * Used for {@code GET /persons/{groupId}}.
     *
     * @throws ResourceNotFoundException if no active current version exists for
     *                                   the group in the current tenant
     */
    @Transactional(readOnly = true)
    public Person findByGroupId(Long groupId) {
        return personRepository
                .findCurrentByGroupId(groupId, tenantService.currentCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("notFound.person|" + groupId));
    }

    /**
     * Returns all current versions (active + inactive) scoped to the tenant.
     * Used for the listing endpoint so the UI can display deactivated persons.
     */
    @Transactional(readOnly = true)
    public List<Person> findAll() {
        return personRepository.findAllCurrentByCompany(tenantService.currentCompanyId());
    }

    // ── Internal / FK resolution ──────────────────────────────────────────────

    /**
     * Finds by entity surrogate ID — for internal FK resolution only.
     * Prefer {@link #findByGroupId(Long)} for user-facing navigation.
     */
    @Transactional(readOnly = true)
    public Person findById(Integer id) {
        Integer companyId = tenantService.currentCompanyId();
        return personRepository.findOne(
                Specification.<Person>where(CommonSpecifications.fieldEquals("id", id))
                        .and(TenantSpecifications.belongsToCompany(companyId))
                        .and(TenantSpecifications.isActive())
        ).orElseThrow(() -> new ResourceNotFoundException("notFound.person|" + id));
    }

    @Transactional(readOnly = true)
    public Optional<Person> findByDocumentNumber(String documentNumber) {
        return personRepository.findOne(
                CommonSpecifications.<Person>fieldEquals("documentNumber", documentNumber)
                        .and(TenantSpecifications.belongsToCompany(tenantService.currentCompanyId()))
                        .and(TenantSpecifications.isActive()));
    }

    // ── Mutations ─────────────────────────────────────────────────────────────

    /**
     * Creates a new person group and its first version.
     *
     * @param request the creation request (validated by the controller)
     * @return the persisted first version
     */
    @Transactional
    public Person create(PersonRequest request) {
        Company company = tenantService.currentCompany();

        PersonGroup group = personGroupRepository.save(
                new PersonGroup(company,
                        request.documentNumber() != null
                                ? request.documentNumber()
                                : "PERSON-PENDING"));

        Person person = buildFromRequest(request, new Person());
        person.setCompany(company);
        person.setGroup(group);
        person.setVersionFrom(LocalDateTime.now());
        person.setVersionTo(null);
        person.setIsCurrent(true);
        return personRepository.save(person);
    }

    /**
     * Updates a person by closing the current version and opening a new one.
     *
     * <p>Historical FK references (DriverAssignment, OperationEvent, etc.)
     * continue pointing to the old version — giving correct historical data.
     *
     * @param groupId the {@link PersonGroup} id (stable business identity)
     * @param request the update request
     * @return the new version
     * @throws ResourceNotFoundException if no current version exists for the group
     */
    @Transactional
    public Person update(Long groupId, PersonRequest request) {
        Integer companyId = tenantService.currentCompanyId();
        Person current = personRepository
                .findCurrentByGroupId(groupId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("notFound.person|" + groupId));

        // Close current version
        LocalDateTime now = LocalDateTime.now();
        current.setVersionTo(now);
        current.setIsCurrent(false);
        personRepository.save(current);

        // Open new version
        Person next = buildFromRequest(request, new Person());
        next.setCompany(current.getCompany());
        next.setGroup(current.getGroup());
        next.setVersionFrom(now);
        next.setVersionTo(null);
        next.setIsCurrent(true);
        return personRepository.save(next);
    }

    /**
     * Deactivates the current version of the person (sets {@code active = false}).
     *
     * <p>The version stays as {@code is_current = true} — there is still a
     * "current" record, it is just inactive.  Use {@link #reactivate(Long)} to
     * create a new active version.
     *
     * @param groupId the group to deactivate
     * @throws BusinessException if the person is the owner of any active vehicle
     */
    @Transactional
    public void deactivate(Long groupId) {
        Integer companyId = tenantService.currentCompanyId();
        Person current = personRepository
                .findCurrentByGroupId(groupId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("notFound.person|" + groupId));

        if (vehicleRepository.existsByOwnerIdAndActiveTrue(current.getId())) {
            throw new BusinessException("fk.personIsVehicleOwner");
        }
        personRepository.softDelete(current);
    }

    /**
     * Reactivates a deactivated person by creating a new active version that
     * copies the last version's data.
     *
     * @param groupId the group to reactivate
     * @return the new active version
     */
    @Transactional
    public Person reactivate(Long groupId) {
        Integer companyId = tenantService.currentCompanyId();
        Person last = personRepository
                .findCurrentByGroupId(groupId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("notFound.person|" + groupId));

        // Close the deactivated version
        LocalDateTime now = LocalDateTime.now();
        last.setVersionTo(now);
        last.setIsCurrent(false);
        personRepository.save(last);

        // Create new active version with same data
        Person next = new Person(
                last.getDocumentNumber(), last.getFirstName(), last.getSecondName(),
                last.getFirstLastName(), last.getSecondLastName());
        next.setActive(true);
        next.setCompany(last.getCompany());
        next.setGroup(last.getGroup());
        next.setVersionFrom(now);
        next.setVersionTo(null);
        next.setIsCurrent(true);
        return personRepository.save(next);
    }

    // ── Legacy method (kept for backward compatibility with filter endpoint) ──

    @Transactional(readOnly = true)
    public Page<Person> filter(FilterRequest request) {
        return personRepository.filter(
                request,
                request.toPageable(),
                TenantSpecifications.<Person>belongsToCompany(tenantService.currentCompanyId())
                        .and(TenantSpecifications.isActive()));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Person buildFromRequest(PersonRequest request, Person person) {
        person.setDocumentNumber(request.documentNumber());
        person.setFirstName(request.firstName());
        person.setSecondName(request.secondName());
        person.setFirstLastName(request.firstLastName());
        person.setSecondLastName(request.secondLastName());
        if (request.active() != null) person.setActive(request.active());
        return person;
    }
}
