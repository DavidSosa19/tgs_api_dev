package com.example.tgs_dev.controller;

import com.example.tgs_dev.controller.exception.ConstraintMessageResolver;
import com.example.tgs_dev.controller.exception.GlobalExceptionHandler;
import com.example.tgs_dev.controller.response.RotationDTO;
import com.example.tgs_dev.controller.response.VehicleRotationDTO;
import com.example.tgs_dev.entity.Route;
import com.example.tgs_dev.entity.RotationEntry;
import com.example.tgs_dev.entity.ScheduleTemplate;
import com.example.tgs_dev.entity.Vehicle;
import com.example.tgs_dev.entity.VehicleRotation;
import com.example.tgs_dev.entity.enums.ShiftDayType;
import com.example.tgs_dev.mapper.RotationMapper;
import com.example.tgs_dev.service.RotationEntryService;
import com.example.tgs_dev.service.ScheduleTemplateService;
import com.example.tgs_dev.service.VehicleRotationService;
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

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("VehicleRotationController")
class VehicleRotationControllerTest {

    @Mock VehicleRotationService vehicleRotationService;
    @Mock RotationEntryService   rotationEntryService;
    @Mock VehicleService         vehicleService;
    @Mock ScheduleTemplateService scheduleTemplateService;
    @Mock RotationMapper         rotationMapper;
    @Mock ConstraintMessageResolver constraintResolver;

    MockMvc mockMvc;
    static final String BASE = "/api/rotations";

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new VehicleRotationController(
                        vehicleRotationService, rotationEntryService,
                        vehicleService, scheduleTemplateService, rotationMapper))
                .setControllerAdvice(new GlobalExceptionHandler(constraintResolver))
                .setValidator(validator)
                .setMessageConverters(new JacksonJsonHttpMessageConverter())
                .build();
    }

    private VehicleRotation rotation(int id) {
        VehicleRotation r = new VehicleRotation(
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31),
                true, ShiftDayType.BUSINESS_DAYS);
        r.setId(id);
        return r;
    }

    private VehicleRotationDTO rotationDTO(int id) {
        return new VehicleRotationDTO(id, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31),
                true, ShiftDayType.BUSINESS_DAYS);
    }

    private RotationDTO fullDTO(int id) {
        return new RotationDTO(rotationDTO(id), List.of());
    }

    @Nested @DisplayName("GET /")
    class GetAll {
        @Test @DisplayName("200 with rotation list")
        void ok() throws Exception {
            when(vehicleRotationService.findAll()).thenReturn(List.of(rotation(1)));
            when(rotationMapper.toRotationDTOList(any())).thenReturn(List.of(rotationDTO(1)));
            mockMvc.perform(get(BASE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray());
        }
    }

    @Nested @DisplayName("GET /entries/{id}")
    class GetEntries {
        @Test @DisplayName("200 with rotation and entries")
        void found() throws Exception {
            VehicleRotation r = rotation(1);
            when(vehicleRotationService.findById(1)).thenReturn(r);
            when(rotationEntryService.findByRotation(r)).thenReturn(List.of());
            when(rotationMapper.toDTO(eq(r), any())).thenReturn(fullDTO(1));
            mockMvc.perform(get(BASE + "/entries/1"))
                    .andExpect(status().isOk());
        }

        @Test @DisplayName("404 when rotation not found")
        void notFound() throws Exception {
            when(vehicleRotationService.findById(99)).thenThrow(new NoSuchElementException("notFound.rotation|99"));
            mockMvc.perform(get(BASE + "/entries/99"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested @DisplayName("POST /")
    class Create {
        @Test @DisplayName("201 with valid body")
        void created() throws Exception {
            VehicleRotation r = rotation(1);
            Vehicle testVehicle = new Vehicle("V-1", null);
            testVehicle.setId(1);
            Route testRoute = new Route("");
            ScheduleTemplate testTemplate = new ScheduleTemplate(testRoute, "T-1", "Template 1", 1);
            testTemplate.setId(1);
            when(rotationMapper.toEntity(any())).thenReturn(r);
            when(vehicleRotationService.save(r)).thenReturn(r);
            when(vehicleService.findById(any())).thenReturn(testVehicle);
            when(scheduleTemplateService.findById(any())).thenReturn(testTemplate);
            when(rotationEntryService.saveAll(eq(r), any())).thenReturn(List.of());
            when(rotationMapper.toDTO(eq(r), any())).thenReturn(fullDTO(1));
            mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"startDate":"2024-01-01","endDate":"2024-01-31",
                                 "rotationType":"BUSINESS_DAYS",
                                 "entries":[{"vehicleId":1,"scheduleTemplateId":1,"listPosition":1}]}"""))
                    .andExpect(status().isCreated());
        }

        @Test @DisplayName("400 when required fields missing")
        void validationFails() throws Exception {
            mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test @DisplayName("400 when entries list is empty")
        void emptyEntriesFails() throws Exception {
            mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"startDate":"2024-01-01","endDate":"2024-01-31",
                                 "rotationType":"BUSINESS_DAYS","entries":[]}"""))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested @DisplayName("PUT /{id}")
    class Update {
        @Test @DisplayName("200 with updated rotation")
        void updated() throws Exception {
            VehicleRotation r = rotation(1);
            List<RotationEntry> existing = List.of();
            Vehicle testVehicle = new Vehicle("V-1", null);
            testVehicle.setId(1);
            Route testRoute = new Route("");
            ScheduleTemplate testTemplate = new ScheduleTemplate(testRoute, "T-1", "Template 1", 1);
            testTemplate.setId(1);
            when(vehicleRotationService.findById(1)).thenReturn(r);
            when(rotationEntryService.findByRotation(r)).thenReturn(existing);
            when(vehicleRotationService.save(r)).thenReturn(r);
            when(vehicleService.findById(any())).thenReturn(testVehicle);
            when(scheduleTemplateService.findById(any())).thenReturn(testTemplate);
            when(rotationEntryService.saveAll(eq(r), any())).thenReturn(List.of());
            when(rotationMapper.toDTO(eq(r), any())).thenReturn(fullDTO(1));
            mockMvc.perform(put(BASE + "/1").contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"startDate":"2024-01-01","endDate":"2024-01-31",
                                 "rotationType":"BUSINESS_DAYS",
                                 "entries":[{"vehicleId":1,"scheduleTemplateId":1,"listPosition":1}]}"""))
                    .andExpect(status().isOk());
            verify(rotationEntryService).deleteAll(existing);
        }
    }

    @Nested @DisplayName("DELETE /{id}")
    class Delete {
        @Test @DisplayName("200 when deleted")
        void deleted() throws Exception {
            VehicleRotation r = rotation(1);
            when(vehicleRotationService.findById(1)).thenReturn(r);
            mockMvc.perform(delete(BASE + "/1"))
                    .andExpect(status().isOk());
            verify(vehicleRotationService).delete(r);
        }

        @Test @DisplayName("404 when not found")
        void notFound() throws Exception {
            when(vehicleRotationService.findById(99)).thenThrow(new NoSuchElementException("notFound.rotation|99"));
            mockMvc.perform(delete(BASE + "/99"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested @DisplayName("POST /filter")
    class Filter {
        @Test @DisplayName("200 with page result")
        void ok() throws Exception {
            when(vehicleRotationService.filter(any())).thenReturn(new PageImpl<>(List.of(rotation(1)), PageRequest.of(0, 10), 1));
            when(rotationMapper.toRotationDTO(any(VehicleRotation.class))).thenReturn(rotationDTO(1));
            mockMvc.perform(post(BASE + "/filter").contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"page":0,"size":10}"""))
                    .andExpect(status().isOk());
        }
    }
}
