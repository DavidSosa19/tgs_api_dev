package com.example.tgs_dev.controller;

import com.example.tgs_dev.controller.exception.ConstraintMessageResolver;
import com.example.tgs_dev.controller.exception.GlobalExceptionHandler;
import com.example.tgs_dev.controller.response.VehicleDTO;
import com.example.tgs_dev.entity.Person;
import com.example.tgs_dev.entity.Vehicle;
import com.example.tgs_dev.mapper.VehicleMapper;
import com.example.tgs_dev.service.PersonService;
import com.example.tgs_dev.service.VehicleService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("VehicleController")
class VehicleControllerTest {

    @Mock VehicleService vehicleService;
    @Mock PersonService  personService;
    @Mock VehicleMapper  vehicleMapper;
    @Mock ConstraintMessageResolver constraintResolver;

    MockMvc mockMvc;
    static final String BASE = "/api/vehicle";

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new VehicleController(vehicleService, personService, vehicleMapper))
                .setControllerAdvice(new GlobalExceptionHandler(constraintResolver))
                .setValidator(validator)
                .setMessageConverters(new JacksonJsonHttpMessageConverter())
                .build();
    }

    private Vehicle vehicle(int id) {
        Vehicle v = new Vehicle("V-" + id, null);
        v.setId(id);
        return v;
    }

    private VehicleDTO dto(int id) {
        return new VehicleDTO(id, "V-" + id, "ABC-" + id, true, null);
    }

    @Nested @DisplayName("GET /")
    class GetAll {
        @Test @DisplayName("200 with vehicle list")
        void ok() throws Exception {
            when(vehicleService.findAll()).thenReturn(List.of(vehicle(1)));
            when(vehicleMapper.toDTOList(any())).thenReturn(List.of(dto(1)));
            mockMvc.perform(get(BASE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray());
        }
    }

    @Nested @DisplayName("GET /{id}")
    class GetById {
        @Test @DisplayName("200 when found")
        void found() throws Exception {
            when(vehicleService.findById(1)).thenReturn(vehicle(1));
            when(vehicleMapper.toDTO(any(Vehicle.class))).thenReturn(dto(1));
            mockMvc.perform(get(BASE + "/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(1));
        }

        @Test @DisplayName("404 when not found")
        void notFound() throws Exception {
            when(vehicleService.findById(99)).thenThrow(new NoSuchElementException("notFound.vehicle|99"));
            mockMvc.perform(get(BASE + "/99"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested @DisplayName("POST /")
    class Create {
        @Test @DisplayName("201 without owner")
        void createdNoOwner() throws Exception {
            Vehicle v = vehicle(1);
            when(vehicleService.save(any())).thenReturn(v);
            when(vehicleMapper.toDTO(v)).thenReturn(dto(1));
            mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"vehicleNumber":"V-1","licensePlate":"ABC-1"}"""))
                    .andExpect(status().isCreated());
        }

        @Test @DisplayName("201 with ownerId resolves person")
        void createdWithOwner() throws Exception {
            Vehicle v = vehicle(1);
            Person owner = new Person("DOC", "John", null, "Doe", null);
            when(personService.findById(5)).thenReturn(owner);
            when(vehicleService.save(any())).thenReturn(v);
            when(vehicleMapper.toDTO(v)).thenReturn(dto(1));
            mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"vehicleNumber":"V-1","licensePlate":"ABC-1","ownerId":5}"""))
                    .andExpect(status().isCreated());
            verify(personService).findById(5);
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
        @Test @DisplayName("200 with updated vehicle")
        void updated() throws Exception {
            Vehicle v = vehicle(1);
            when(vehicleService.findById(1)).thenReturn(v);
            when(vehicleService.save(any())).thenReturn(v);
            when(vehicleMapper.toDTO(v)).thenReturn(dto(1));
            mockMvc.perform(put(BASE + "/1").contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"vehicleNumber":"V-1","licensePlate":"UPD-1"}"""))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(1));
        }

        @Test @DisplayName("404 when vehicle not found")
        void notFound() throws Exception {
            when(vehicleService.findById(99)).thenThrow(new NoSuchElementException("notFound.vehicle|99"));
            mockMvc.perform(put(BASE + "/99").contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"vehicleNumber":"V-1","licensePlate":"ABC-1"}"""))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested @DisplayName("DELETE /{id}")
    class Delete {
        @Test @DisplayName("200 when deleted")
        void deleted() throws Exception {
            Vehicle v = vehicle(1);
            when(vehicleService.findById(1)).thenReturn(v);
            mockMvc.perform(delete(BASE + "/1"))
                    .andExpect(status().isOk());
            verify(vehicleService).delete(v);
        }

        @Test @DisplayName("404 when not found")
        void notFound() throws Exception {
            when(vehicleService.findById(99)).thenThrow(new NoSuchElementException("notFound.vehicle|99"));
            mockMvc.perform(delete(BASE + "/99"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested @DisplayName("POST /filter")
    class Filter {
        @Test @DisplayName("200 with page result")
        void ok() throws Exception {
            when(vehicleService.filter(any())).thenReturn(new PageImpl<>(List.of(vehicle(1)), PageRequest.of(0, 10), 1));
            when(vehicleMapper.toDTO(any(Vehicle.class))).thenReturn(dto(1));
            mockMvc.perform(post(BASE + "/filter").contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"page":0,"size":10}"""))
                    .andExpect(status().isOk());
        }
    }
}
