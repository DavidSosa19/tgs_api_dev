package com.example.tgs_dev.service;

import com.example.tgs_dev.controller.request.PersonRequest;
import com.example.tgs_dev.controller.request.RegisterRequest;
import com.example.tgs_dev.entity.Person;
import com.example.tgs_dev.entity.User;
import com.example.tgs_dev.entity.enums.Role;
import com.example.tgs_dev.mapper.PersonMapper;
import com.example.tgs_dev.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService")
class UserServiceTest {

    @Mock UserRepository userRepo;
    @Mock PasswordEncoder passwordEncoder;
    @Mock PersonService personService;
    @Mock PersonMapper personMapper;
    @InjectMocks UserService sut;

    private RegisterRequest registerRequest() {
        PersonRequest pr = new PersonRequest("DOC-1", "Jane", null, "Doe", null, null);
        return new RegisterRequest("jdoe", "secret", pr);
    }

    @Nested @DisplayName("signUpUser")
    class SignUpUser {

        @Test @DisplayName("throws IllegalArgumentException when person already exists")
        void personAlreadyExists_throws() {
            Person existing = new Person("DOC-1", "Jane", null, "Doe", null);
            when(personService.findByDocumentNumber("DOC-1")).thenReturn(Optional.of(existing));

            RegisterRequest req = registerRequest();
            assertThatThrownBy(() -> sut.signUpUser(req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Person already exists");
        }

        @Test @DisplayName("creates person and user when document number is new")
        void newPerson_createsUserAndPerson() {
            Person savedPerson = new Person("DOC-1", "Jane", null, "Doe", null);
            savedPerson.setId(1);
            User savedUser = User.builder().userName("jdoe").password("encoded").rol(Role.USER).build();

            when(personService.findByDocumentNumber("DOC-1")).thenReturn(Optional.empty());
            when(personMapper.toEntity(any())).thenReturn(savedPerson);
            when(personService.save(savedPerson)).thenReturn(savedPerson);
            when(passwordEncoder.encode("secret")).thenReturn("encoded");
            when(userRepo.save(any(User.class))).thenReturn(savedUser);

            User result = sut.signUpUser(registerRequest());

            assertThat(result).isSameAs(savedUser);
            verify(personService).save(savedPerson);
            verify(userRepo).save(any(User.class));
        }

        @Test @DisplayName("saved user has the encoded password, not plaintext")
        void newPerson_passwordIsEncoded() {
            Person savedPerson = new Person("DOC-1", "Jane", null, "Doe", null);
            savedPerson.setId(1);

            when(personService.findByDocumentNumber("DOC-1")).thenReturn(Optional.empty());
            when(personMapper.toEntity(any())).thenReturn(savedPerson);
            when(personService.save(any())).thenReturn(savedPerson);
            when(passwordEncoder.encode("secret")).thenReturn("encoded-pw");
            when(userRepo.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            User result = sut.signUpUser(registerRequest());

            assertThat(result.getPassword()).isEqualTo("encoded-pw");
            assertThat(result.getPassword()).isNotEqualTo("secret");
        }
    }
}
