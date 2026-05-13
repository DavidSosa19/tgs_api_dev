package com.example.tgs_dev.mapper;

import com.example.tgs_dev.controller.request.PersonRequest;
import com.example.tgs_dev.controller.response.PersonDTO;
import com.example.tgs_dev.entity.Person;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PersonMapper {

    public PersonDTO toDTO(Person person) {
        if (person == null) return null;
        return new PersonDTO(
                person.getId(),
                person.getDocumentNumber(),
                person.getFirstName(),
                person.getSecondName(),
                person.getFirstLastName(),
                person.getSecondLastName(),
                person.getActive()
        );
    }

    public List<PersonDTO> toDTOList(List<Person> persons) {
        return persons.stream().map(this::toDTO).toList();
    }

    public Person toEntity(PersonRequest request) {
        Person person = new Person(
                request.documentNumber(),
                request.firstName(),
                request.secondName(),
                request.firstLastName(),
                request.secondLastName()
        );
        if (request.active() != null) {
            person.setActive(request.active());
        }
        return person;
    }

    public void updateEntity(Person person, PersonRequest request) {
        person.setDocumentNumber(request.documentNumber());
        person.setFirstName(request.firstName());
        person.setSecondName(request.secondName());
        person.setFirstLastName(request.firstLastName());
        person.setSecondLastName(request.secondLastName());
        if (request.active() != null) {
            person.setActive(request.active());
        }
    }
}
