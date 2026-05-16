package com.example.tgs_dev.controller;

import com.example.tgs_dev.controller.exception.ConstraintMessageResolver;
import com.example.tgs_dev.controller.exception.GlobalExceptionHandler;
import com.example.tgs_dev.controller.response.PersonDTO;
import com.example.tgs_dev.entity.Person;
import com.example.tgs_dev.mapper.PersonMapper;
import com.example.tgs_dev.service.PersonService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;
import java.util.NoSuchElementException;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PersonController")
class PersonControllerTest {

    @Mock PersonService personService;
    @Mock PersonMapper  personMapper;
    @Mock ConstraintMessageResolver constraintResolver;

    MockMvc mockMvc;
    static final String BASE = "/api/person";

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new PersonController(personService, personMapper))
                .setControllerAdvice(new GlobalExceptionHandler(constraintResolver))
                .setValidator(validator)
                .setMessageConverters(new JacksonJsonHttpMessageConverter())
                .build();
    }

    private Person person(int id) {
        Person p = new Person("DOC-" + id, "John", null, "Doe", null);
        p.setId(id);
        return p;
    }

    private PersonDTO dto(int id) {
        return new PersonDTO(id, "DOC-" + id, "John", null, "Doe", null, true);
    }

    @Nested @DisplayName("GET /")
    class GetAll {
        @Test @DisplayName("200 with person list")
        void ok() throws Exception {
            when(personService.findAll()).thenReturn(List.of(person(1)));
            when(personMapper.toDTOList(any())).thenReturn(List.of(dto(1)));
            mockMvc.perform(get(BASE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray());
        }
    }

    @Nested @DisplayName("GET /{id}")
    class GetById {
        @Test @DisplayName("200 when found")
        void found() throws Exception {
            when(personService.findById(1)).thenReturn(person(1));
            when(personMapper.toDTO(any(Person.class))).thenReturn(dto(1));
            mockMvc.perform(get(BASE + "/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(1));
        }

        @Test @DisplayName("404 when not found")
        void notFound() throws Exception {
            when(personService.findById(99)).thenThrow(new NoSuchElementException("notFound.person|99"));
            mockMvc.perform(get(BASE + "/99"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested @DisplayName("POST /")
    class Create {
        @Test @DisplayName("201 with valid body")
        void created() throws Exception {
            Person p = person(1);
            when(personMapper.toEntity(any())).thenReturn(p);
            when(personService.save(p)).thenReturn(p);
            when(personMapper.toDTO(p)).thenReturn(dto(1));
            mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"documentNumber":"DOC-1","firstName":"John",
                                 "firstLastName":"Doe"}"""))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test @DisplayName("400 when required fields missing")
        void validationFails() throws Exception {
            mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested @DisplayName("PUT /{id}")
    class Update {
        @Test @DisplayName("200 with updated person")
        void updated() throws Exception {
            Person p = person(1);
            when(personService.findById(1)).thenReturn(p);
            when(personService.save(p)).thenReturn(p);
            when(personMapper.toDTO(p)).thenReturn(dto(1));
            mockMvc.perform(put(BASE + "/1").contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"documentNumber":"DOC-1","firstName":"John",
                                 "firstLastName":"Doe"}"""))
                    .andExpect(status().isOk());
        }
    }

    @Nested @DisplayName("DELETE /{id}")
    class Delete {
        @Test @DisplayName("200 when deleted")
        void deleted() throws Exception {
            Person p = person(1);
            when(personService.findById(1)).thenReturn(p);
            mockMvc.perform(delete(BASE + "/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
            verify(personService).delete(p);
        }

        @Test @DisplayName("404 when not found")
        void notFound() throws Exception {
            when(personService.findById(99)).thenThrow(new NoSuchElementException("notFound.person|99"));
            mockMvc.perform(delete(BASE + "/99"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested @DisplayName("POST /filter")
    class Filter {
        @Test @DisplayName("200 with page result")
        void ok() throws Exception {
            when(personService.filter(any())).thenReturn(new PageImpl<>(List.of(person(1)), PageRequest.of(0, 10), 1));
            when(personMapper.toDTO(any(Person.class))).thenReturn(dto(1));
            mockMvc.perform(post(BASE + "/filter").contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"page":0,"size":10}"""))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content").isArray());
        }
    }
}
