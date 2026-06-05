package com.example.tgs_dev.controller;

import com.example.tgs_dev.controller.exception.ConstraintMessageResolver;
import com.example.tgs_dev.controller.exception.GlobalExceptionHandler;
import com.example.tgs_dev.controller.request.VehicleRequest;
import com.example.tgs_dev.controller.response.VehicleDTO;
import com.example.tgs_dev.entity.Vehicle;
import com.example.tgs_dev.mapper.VehicleMapper;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("VehicleController")
class VehicleControllerTest {

    @Mock VehicleService vehicleService;
    @Mock VehicleMapper  vehicleMapper;
    @Mock ConstraintMessageResolver constraintResolver;

    MockMvc mockMvc;
    static final String BASE = "/api/vehicles";

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new VehicleController(vehicleService, vehicleMapper))
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

    private VehicleDTO dto(int id, long groupId) {
        return new VehicleDTO(id, groupId, "V-" + id, "ABC-" + id, true, null);
    }

    @Nested @DisplayName("GET /")
    class GetAll {
        @Test @DisplayName("200 with vehicle list")
        void ok() throws Exception {
            when(vehicleService.findAll()).thenReturn(List.of(vehicle(1)));
            when(vehicleMapper.toDTOList(any())).thenReturn(List.of(dto(1, 50L)));
            mockMvc.perform(get(BASE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray());
        }
    }

    @Nested @DisplayName("GET /{groupId}")
    class GetById {
        @Test @DisplayName("200 when found")
        void found() throws Exception {
            when(vehicleService.findByGroupId(50L)).thenReturn(vehicle(1));
            when(vehicleMapper.toDTO(any(Vehicle.class))).thenReturn(dto(1, 50L));
            mockMvc.perform(get(BASE + "/50"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.groupId").value(50));
        }
    }

    @Nested @DisplayName("POST /")
    class Create {
        @Test @DisplayName("201 with valid body")
        void created() throws Exception {
            when(vehicleService.create(any(VehicleRequest.class))).thenReturn(vehicle(1));
            when(vehicleMapper.toDTO(any(Vehicle.class))).thenReturn(dto(1, 50L));
            mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"vehicleNumber":"V-1","licensePlate":"ABC-1"}"""))
                    .andExpect(status().isCreated());
        }

        @Test @DisplayName("400 when required fields missing")
        void validationFails() throws Exception {
            mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested @DisplayName("PUT /{groupId}")
    class Update {
        @Test @DisplayName("200 with updated vehicle")
        void updated() throws Exception {
            when(vehicleService.update(eq(50L), any(VehicleRequest.class))).thenReturn(vehicle(1));
            when(vehicleMapper.toDTO(any(Vehicle.class))).thenReturn(dto(1, 50L));
            mockMvc.perform(put(BASE + "/50").contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"vehicleNumber":"V-1","licensePlate":"UPD-1"}"""))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.groupId").value(50));
        }
    }

    @Nested @DisplayName("DELETE /{groupId}")
    class Delete {
        @Test @DisplayName("200 when deactivated")
        void deleted() throws Exception {
            mockMvc.perform(delete(BASE + "/50"))
                    .andExpect(status().isOk());
            verify(vehicleService).deactivate(50L);
        }
    }

    @Nested @DisplayName("PATCH /{groupId}/reactivate")
    class Reactivate {
        @Test @DisplayName("200 when reactivated")
        void reactivated() throws Exception {
            when(vehicleService.reactivate(50L)).thenReturn(vehicle(1));
            mockMvc.perform(patch(BASE + "/50/reactivate"))
                    .andExpect(status().isOk());
            verify(vehicleService).reactivate(50L);
        }
    }

    @Nested @DisplayName("POST /filter")
    class Filter {
        @Test @DisplayName("200 with page result")
        void ok() throws Exception {
            when(vehicleService.filter(any())).thenReturn(new PageImpl<>(List.of(vehicle(1)), PageRequest.of(0, 10), 1));
            when(vehicleMapper.toDTO(any(Vehicle.class))).thenReturn(dto(1, 50L));
            mockMvc.perform(post(BASE + "/filter").contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"page":0,"size":10}"""))
                    .andExpect(status().isOk());
        }
    }
}
