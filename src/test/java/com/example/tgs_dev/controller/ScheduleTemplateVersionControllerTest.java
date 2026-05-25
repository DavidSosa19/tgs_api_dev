package com.example.tgs_dev.controller;

import com.example.tgs_dev.controller.exception.ConflictException;
import com.example.tgs_dev.controller.exception.ConstraintMessageResolver;
import com.example.tgs_dev.controller.exception.GlobalExceptionHandler;
import com.example.tgs_dev.controller.exception.ResourceNotFoundException;
import com.example.tgs_dev.entity.Route;
import com.example.tgs_dev.entity.ScheduleTemplate;
import com.example.tgs_dev.entity.ScheduleTemplateVersion;
import com.example.tgs_dev.service.ScheduleTemplateVersionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static com.example.tgs_dev.TestFixtures.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScheduleTemplateVersionController")
class ScheduleTemplateVersionControllerTest {

    @Mock ScheduleTemplateVersionService service;
    @Mock ConstraintMessageResolver      constraintResolver;

    MockMvc mockMvc;

    private static final Route           ROUTE    = route(10, "R-10");
    private static final ScheduleTemplate TEMPLATE = template(5, ROUTE, LocalTime.of(6, 0));
    private static final String           BASE     = "/api/schedule-templates/5/versions";

    private static final LocalDate FROM    = LocalDate.of(2024, 12, 1);
    private static final LocalDate TO      = LocalDate.of(2025, 1, 14);
    private static final LocalTime T_07_00 = LocalTime.of(7, 0);

    private static final String VALID_BODY = """
            {
              "label":         "Horario vacacional dic-ene",
              "startTime":     "07:00:00",
              "effectiveFrom": "2024-12-01",
              "effectiveTo":   "2025-01-14"
            }
            """;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ScheduleTemplateVersionController(service))
                .setControllerAdvice(new GlobalExceptionHandler(constraintResolver))
                .setValidator(validator)
                .setMessageConverters(new JacksonJsonHttpMessageConverter())
                .build();
    }

    private ScheduleTemplateVersion version(int id) {
        return templateVersion(id, TEMPLATE, T_07_00, FROM, TO);
    }

    // ── GET /versions ─────────────────────────────────────────────────────────

    @Nested @DisplayName("GET /versions")
    class FindAll {

        @Test @DisplayName("200 with list of version DTOs")
        void ok() throws Exception {
            when(service.findAllByTemplate(5)).thenReturn(List.of(version(1), version(2)));

            mockMvc.perform(get(BASE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data[0].id").value(1))
                    .andExpect(jsonPath("$.data[0].startTime").value("07:00:00"))
                    .andExpect(jsonPath("$.data[0].effectiveFrom").value("2024-12-01"));
        }

        @Test @DisplayName("200 with empty list when template has no versions")
        void empty() throws Exception {
            when(service.findAllByTemplate(5)).thenReturn(List.of());
            mockMvc.perform(get(BASE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(0));
        }

        @Test @DisplayName("404 when template not found")
        void templateNotFound() throws Exception {
            when(service.findAllByTemplate(5))
                    .thenThrow(new ResourceNotFoundException("notFound.scheduleTemplate|5"));
            mockMvc.perform(get(BASE)).andExpect(status().isNotFound());
        }
    }

    // ── GET /versions/{id} ────────────────────────────────────────────────────

    @Nested @DisplayName("GET /versions/{id}")
    class FindById {

        @Test @DisplayName("200 with single version DTO")
        void ok() throws Exception {
            when(service.findById(5, 1)).thenReturn(version(1));
            mockMvc.perform(get(BASE + "/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(1));
        }

        @Test @DisplayName("404 when version not found")
        void notFound() throws Exception {
            when(service.findById(5, 99))
                    .thenThrow(new ResourceNotFoundException("notFound.scheduleTemplateVersion|99"));
            mockMvc.perform(get(BASE + "/99")).andExpect(status().isNotFound());
        }
    }

    // ── POST /versions ────────────────────────────────────────────────────────

    @Nested @DisplayName("POST /versions")
    class Create {

        @Test @DisplayName("201 with created version DTO")
        void created() throws Exception {
            when(service.create(eq(5), any())).thenReturn(version(1));

            mockMvc.perform(post(BASE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.startTime").value("07:00:00"));
        }

        @Test @DisplayName("409 when date range overlaps existing version")
        void conflict() throws Exception {
            when(service.create(eq(5), any()))
                    .thenThrow(new ConflictException("conflict.scheduleTemplateVersion.overlap"));

            mockMvc.perform(post(BASE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY))
                    .andExpect(status().isConflict());
        }

        @Test @DisplayName("400 when startTime or effectiveFrom are missing")
        void validationFails() throws Exception {
            mockMvc.perform(post(BASE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ── PUT /versions/{id} ────────────────────────────────────────────────────

    @Nested @DisplayName("PUT /versions/{id}")
    class Update {

        @Test @DisplayName("200 with updated version DTO")
        void ok() throws Exception {
            when(service.update(eq(5), eq(1), any())).thenReturn(version(1));

            mockMvc.perform(put(BASE + "/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(1));
        }

        @Test @DisplayName("404 when version not found")
        void notFound() throws Exception {
            when(service.update(eq(5), eq(99), any()))
                    .thenThrow(new ResourceNotFoundException("notFound.scheduleTemplateVersion|99"));

            mockMvc.perform(put(BASE + "/99")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY))
                    .andExpect(status().isNotFound());
        }
    }

    // ── DELETE /versions/{id} ─────────────────────────────────────────────────

    @Nested @DisplayName("DELETE /versions/{id}")
    class Delete {

        @Test @DisplayName("200 when version deleted successfully")
        void ok() throws Exception {
            doNothing().when(service).delete(5, 1);

            mockMvc.perform(delete(BASE + "/1"))
                    .andExpect(status().isOk());
            verify(service).delete(5, 1);
        }

        @Test @DisplayName("404 when version not found")
        void notFound() throws Exception {
            doThrow(new ResourceNotFoundException("notFound.scheduleTemplateVersion|99"))
                    .when(service).delete(5, 99);

            mockMvc.perform(delete(BASE + "/99"))
                    .andExpect(status().isNotFound());
        }
    }
}
