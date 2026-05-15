package com.example.tgs_dev.service;

import com.example.tgs_dev.controller.exception.ResourceNotFoundException;
import com.example.tgs_dev.entity.Person;
import com.example.tgs_dev.repository.PersonRepository;
import com.example.tgs_dev.repository.filter.FilterRequest;
import com.example.tgs_dev.repository.specification.CommonSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class PersonService {

    private final PersonRepository personRepository;

    public PersonService(PersonRepository personRepository) {
        this.personRepository = personRepository;
    }

    public Person save(Person person){
        return personRepository.save(person);
    }

    public Person findById(Integer id){
        return personRepository.findById(id).
                orElseThrow(() -> new ResourceNotFoundException("notFound.person|" + id));
    }

    public Optional<Person> findByDocumentNumber(String documentNumber){
        return personRepository.findOne(CommonSpecifications.fieldEquals("documentNumber", documentNumber));
    }

    public List<Person> findAll(){ return personRepository.findAll(); }

    public void delete(Person person){
        personRepository.softDelete(person);
    }

    public Page<Person> filter(FilterRequest request) {
        return personRepository.filter(request, request.toPageable());
    }
}
