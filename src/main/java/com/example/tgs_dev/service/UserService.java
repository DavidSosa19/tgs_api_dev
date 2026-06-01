package com.example.tgs_dev.service;

import com.example.tgs_dev.controller.request.RegisterRequest;
import com.example.tgs_dev.entity.AppRoleEntity;
import com.example.tgs_dev.entity.Company;
import com.example.tgs_dev.entity.Person;
import com.example.tgs_dev.entity.User;
import com.example.tgs_dev.repository.AppRoleRepository;
import com.example.tgs_dev.repository.CompanyRepository;
import com.example.tgs_dev.repository.UserRepository;
import com.example.tgs_dev.security.AppRole;
import com.example.tgs_dev.security.TenantContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
public class UserService {

    private final UserRepository    userRepository;
    private final PasswordEncoder   passwordEncoder;
    private final PersonService     personService;
    private final AppRoleRepository appRoleRepository;
    private final CompanyRepository companyRepository;

    public UserService(UserRepository    userRepository,
                       PasswordEncoder   passwordEncoder,
                       PersonService     personService,
                       AppRoleRepository appRoleRepository,
                       CompanyRepository companyRepository) {
        this.userRepository    = userRepository;
        this.passwordEncoder   = passwordEncoder;
        this.personService     = personService;
        this.appRoleRepository = appRoleRepository;
        this.companyRepository = companyRepository;
    }

    @Transactional
    public User signUpUser(RegisterRequest request) {
        // Resolve the company from the request (registration is public; no TenantContext).
        Company company = companyRepository.findById(request.companyId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Company not found: " + request.companyId()));

        // Scope document-number uniqueness check to the same company by temporarily
        // setting the tenant context (PersonService.findByDocumentNumber uses it).
        TenantContext.set(company.getId());
        try {
            Optional<Person> existing = personService.findByDocumentNumber(
                    request.person().documentNumber());

            if (existing.isPresent()) {
                throw new IllegalArgumentException("Person already exists in database");
            }

            // SCD-aware creation: builds the person_group + first version.
            Person savedPerson = personService.create(request.person());

            AppRoleEntity defaultRole = appRoleRepository.findByName(AppRole.USER)
                    .orElseThrow(() -> new IllegalStateException(
                            "Default USER role not found. Run the V2 RBAC migration script."));

            User newUser = new User(
                    request.userName(),
                    Objects.requireNonNull(passwordEncoder.encode(request.password())),
                    Set.of(defaultRole),
                    savedPerson,
                    company
            );
            return userRepository.save(newUser);
        } finally {
            TenantContext.clear();
        }
    }
}
