package com.example.tgs_dev.service;

import com.example.tgs_dev.controller.request.PersonRequest;
import com.example.tgs_dev.controller.request.RegisterRequest;
import com.example.tgs_dev.entity.AppRoleEntity;
import com.example.tgs_dev.entity.Company;
import com.example.tgs_dev.entity.Person;
import com.example.tgs_dev.entity.User;
import com.example.tgs_dev.mapper.PersonMapper;
import com.example.tgs_dev.repository.AppRoleRepository;
import com.example.tgs_dev.repository.CompanyRepository;
import com.example.tgs_dev.repository.UserRepository;
import com.example.tgs_dev.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashSet;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService")
class UserServiceTest {

    @Mock UserRepository    userRepo;
    @Mock PasswordEncoder   passwordEncoder;
    @Mock PersonService     personService;
    @Mock PersonMapper      personMapper;
    @Mock AppRoleRepository appRoleRepository;
    @Mock CompanyRepository companyRepository;
    @InjectMocks UserService sut;

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    private RegisterRequest registerRequest() {
        PersonRequest pr = new PersonRequest("DOC-1", "Jane", null, "Doe", null, null);
        return new RegisterRequest("jdoe", "secret12", pr, 1);
    }

    private AppRoleEntity userRole() {
        AppRoleEntity role = new AppRoleEntity();
        role.setName("USER");
        return role;
    }

    private Company company() {
        Company c = new Company("ACME", "123-456");
        c.setId(1);
        return c;
    }

    @Nested @DisplayName("signUpUser")
    class SignUpUser {

        @Test @DisplayName("lanza IllegalArgumentException cuando la empresa no existe")
        void companyNotFound_throws() {
            when(companyRepository.findById(1)).thenReturn(Optional.empty());

            RegisterRequest req = registerRequest();
            assertThatThrownBy(() -> sut.signUpUser(req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Company not found");
        }

        @Test @DisplayName("lanza IllegalArgumentException cuando la persona ya existe")
        void personAlreadyExists_throws() {
            when(companyRepository.findById(1)).thenReturn(Optional.of(company()));
            Person existing = new Person("DOC-1", "Jane", null, "Doe", null);
            when(personService.findByDocumentNumber("DOC-1")).thenReturn(Optional.of(existing));

            RegisterRequest req = registerRequest();
            assertThatThrownBy(() -> sut.signUpUser(req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Person already exists");
        }

        @Test @DisplayName("lanza IllegalStateException cuando el rol USER no existe en BD")
        void missingDefaultRole_throws() {
            Person savedPerson = new Person("DOC-1", "Jane", null, "Doe", null);
            savedPerson.setId(1);
            when(companyRepository.findById(1)).thenReturn(Optional.of(company()));
            when(personService.findByDocumentNumber("DOC-1")).thenReturn(Optional.empty());
            when(personMapper.toEntity(any())).thenReturn(savedPerson);
            when(personService.save(any())).thenReturn(savedPerson);
            when(appRoleRepository.findByName("USER")).thenReturn(Optional.empty());

            RegisterRequest req = registerRequest();
            assertThatThrownBy(() -> sut.signUpUser(req))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("USER role not found");
        }

        @Test @DisplayName("crea persona y usuario cuando el documento es nuevo")
        void newPerson_createsUserAndPerson() {
            Person savedPerson = new Person("DOC-1", "Jane", null, "Doe", null);
            savedPerson.setId(1);
            User savedUser = User.builder()
                    .userName("jdoe")
                    .password("encoded")
                    .roles(new HashSet<>())
                    .build();

            when(companyRepository.findById(1)).thenReturn(Optional.of(company()));
            when(personService.findByDocumentNumber("DOC-1")).thenReturn(Optional.empty());
            when(personMapper.toEntity(any())).thenReturn(savedPerson);
            when(personService.save(savedPerson)).thenReturn(savedPerson);
            when(passwordEncoder.encode("secret12")).thenReturn("encoded");
            when(appRoleRepository.findByName("USER")).thenReturn(Optional.of(userRole()));
            when(userRepo.save(any(User.class))).thenReturn(savedUser);

            User result = sut.signUpUser(registerRequest());

            assertThat(result).isSameAs(savedUser);
            verify(personService).save(savedPerson);
            verify(userRepo).save(any(User.class));
        }

        @Test @DisplayName("el usuario guardado lleva el password encoded, nunca el plaintext")
        void newPerson_passwordIsEncoded() {
            Person savedPerson = new Person("DOC-1", "Jane", null, "Doe", null);
            savedPerson.setId(1);

            when(companyRepository.findById(1)).thenReturn(Optional.of(company()));
            when(personService.findByDocumentNumber("DOC-1")).thenReturn(Optional.empty());
            when(personMapper.toEntity(any())).thenReturn(savedPerson);
            when(personService.save(any())).thenReturn(savedPerson);
            when(passwordEncoder.encode("secret12")).thenReturn("encoded-pw");
            when(appRoleRepository.findByName("USER")).thenReturn(Optional.of(userRole()));
            when(userRepo.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            User result = sut.signUpUser(registerRequest());

            assertThat(result.getPassword()).isEqualTo("encoded-pw");
            assertThat(result.getPassword()).isNotEqualTo("secret12");
        }

        @Test @DisplayName("el nuevo usuario recibe el rol USER de la base de datos")
        void newPerson_receivesUserRole() {
            Person savedPerson = new Person("DOC-1", "Jane", null, "Doe", null);
            savedPerson.setId(1);
            AppRoleEntity role = userRole();

            when(companyRepository.findById(1)).thenReturn(Optional.of(company()));
            when(personService.findByDocumentNumber("DOC-1")).thenReturn(Optional.empty());
            when(personMapper.toEntity(any())).thenReturn(savedPerson);
            when(personService.save(any())).thenReturn(savedPerson);
            when(passwordEncoder.encode("secret12")).thenReturn("encoded-pw");
            when(appRoleRepository.findByName("USER")).thenReturn(Optional.of(role));
            when(userRepo.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            User result = sut.signUpUser(registerRequest());

            assertThat(result.getRoles()).containsExactly(role);
        }

        @Test @DisplayName("el nuevo usuario queda asociado a la empresa correcta")
        void newPerson_linkedToCorrectCompany() {
            Person savedPerson = new Person("DOC-1", "Jane", null, "Doe", null);
            savedPerson.setId(1);
            Company expected = company();

            when(companyRepository.findById(1)).thenReturn(Optional.of(expected));
            when(personService.findByDocumentNumber("DOC-1")).thenReturn(Optional.empty());
            when(personMapper.toEntity(any())).thenReturn(savedPerson);
            when(personService.save(any())).thenReturn(savedPerson);
            when(passwordEncoder.encode("secret12")).thenReturn("encoded-pw");
            when(appRoleRepository.findByName("USER")).thenReturn(Optional.of(userRole()));
            when(userRepo.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            User result = sut.signUpUser(registerRequest());

            assertThat(result.getCompany()).isSameAs(expected);
        }

        @Test @DisplayName("TenantContext queda limpio tras el registro (éxito)")
        void tenantContext_clearedAfterSuccess() {
            Person savedPerson = new Person("DOC-1", "Jane", null, "Doe", null);
            savedPerson.setId(1);

            when(companyRepository.findById(1)).thenReturn(Optional.of(company()));
            when(personService.findByDocumentNumber("DOC-1")).thenReturn(Optional.empty());
            when(personMapper.toEntity(any())).thenReturn(savedPerson);
            when(personService.save(any())).thenReturn(savedPerson);
            when(passwordEncoder.encode("secret12")).thenReturn("encoded-pw");
            when(appRoleRepository.findByName("USER")).thenReturn(Optional.of(userRole()));
            when(userRepo.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            sut.signUpUser(registerRequest());

            assertThat(TenantContext.get()).isNull();
        }

        @Test @DisplayName("TenantContext queda limpio tras el registro (fallo)")
        void tenantContext_clearedAfterFailure() {
            when(companyRepository.findById(1)).thenReturn(Optional.of(company()));
            when(personService.findByDocumentNumber("DOC-1"))
                    .thenReturn(Optional.of(new Person("DOC-1", "Jane", null, "Doe", null)));

            RegisterRequest req = registerRequest();
            assertThatThrownBy(() -> sut.signUpUser(req))
                    .isInstanceOf(IllegalArgumentException.class);

            assertThat(TenantContext.get()).isNull();
        }
    }
}
