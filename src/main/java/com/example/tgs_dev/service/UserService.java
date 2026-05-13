package com.example.tgs_dev.service;

import com.example.tgs_dev.controller.exception.ResourceNotFoundException;
import com.example.tgs_dev.controller.request.RegisterRequest;
import com.example.tgs_dev.entity.Person;
import com.example.tgs_dev.entity.User;
import com.example.tgs_dev.entity.enums.Role;
import com.example.tgs_dev.mapper.PersonMapper;
import com.example.tgs_dev.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PersonService personService;
    private final PersonMapper personMapper;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       PersonService personService,
                       PersonMapper personMapper) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.personService = personService;
        this.personMapper = personMapper;
    }

    @Transactional
    public User signUpUser(RegisterRequest request) {
        Optional<Person> existing = personService.findByDocumentNumber(
                request.person().documentNumber());

        if (existing.isPresent()) {
            throw new IllegalArgumentException("Person already exists in database");
        }

        Person newPerson = personMapper.toEntity(request.person());
        Person savedPerson = personService.save(newPerson);

        User newUser = new User(
                request.userName(),
                Objects.requireNonNull(passwordEncoder.encode(request.password())),
                Role.USER,
                savedPerson
        );
        return userRepository.save(newUser);
    }
}
