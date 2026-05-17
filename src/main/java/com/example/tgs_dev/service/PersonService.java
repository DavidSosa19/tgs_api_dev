package com.example.tgs_dev.service;

import com.example.tgs_dev.controller.exception.ResourceNotFoundException;
import com.example.tgs_dev.entity.Person;
import com.example.tgs_dev.repository.PersonRepository;
import com.example.tgs_dev.repository.filter.FilterRequest;
import com.example.tgs_dev.repository.specification.CommonSpecifications;
import com.example.tgs_dev.repository.specification.TenantSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class PersonService {

    private final PersonRepository personRepository;
    private final TenantService    tenantService;

    public PersonService(PersonRepository personRepository, TenantService tenantService) {
        this.personRepository = personRepository;
        this.tenantService    = tenantService;
    }

    public Person save(Person person) {
        person.setCompany(tenantService.currentCompany());
        return personRepository.save(person);
    }

    public Person findById(Integer id) {
        Integer companyId = tenantService.currentCompanyId();
        return personRepository.findOne(
                Specification.<Person>where(CommonSpecifications.fieldEquals("id", id))
                        .and(TenantSpecifications.belongsToCompany(companyId))
        ).orElseThrow(() -> new ResourceNotFoundException("notFound.person|" + id));
    }

    public Optional<Person> findByDocumentNumber(String documentNumber) {
        return personRepository.findOne(
                CommonSpecifications.<Person>fieldEquals("documentNumber", documentNumber)
                        .and(TenantSpecifications.belongsToCompany(tenantService.currentCompanyId())));
    }

    public List<Person> findAll() {
        return personRepository.findAll(
                TenantSpecifications.belongsToCompany(tenantService.currentCompanyId()));
    }

    public void delete(Person person) {
        personRepository.softDelete(person);
    }

    public Page<Person> filter(FilterRequest request) {
        return personRepository.filter(
                request,
                request.toPageable(),
                TenantSpecifications.belongsToCompany(tenantService.currentCompanyId()));
    }
}
