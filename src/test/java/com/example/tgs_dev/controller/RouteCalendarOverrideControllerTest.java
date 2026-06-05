package com.example.tgs_dev.controller;

import com.example.tgs_dev.controller.exception.ConstraintMessageResolver;
import com.example.tgs_dev.controller.exception.GlobalExceptionHandler;
import com.example.tgs_dev.controller.exception.ResourceNotFoundException;
import com.example.tgs_dev.entity.RouteCalendarOverride;
import com.example.tgs_dev.service.RouteCalendarOverrideService;
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
import java.util.List;

import static com.example.tgs_dev.TestFixtures.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RouteCalendarOverrideController")
class RouteCalendarOverrideControllerTest {

    @Mock RouteCalendarOverrideService service;
    @Mock ConstraintMessageResolver    constraintResolver;

    MockMvc mockMvc;

    private static final int    ROUTE_ID = 10;
    private static final String BASE     = "/api/routes/10/overrides";

    private static final String VALID_BODY = """
            {
              "overrideDate":  "2024-06-15",
              "useTimeRanges": false,
              "baseDuration":  30
            }
            """;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new RouteCalendarOverrideController(service))
                .setControllerAdvice(new GlobalExceptionHandler(constraintResolver))
                .setValidator(validator)
                .setMessageConverters(new JacksonJsonHttpMessageConverter())
                .build();
    }

    private RouteCalendarOverride override(int id) {
        RouteCalendarOverride ov = new RouteCalendarOverride(
                route(ROUTE_ID, "R-10"),
                company(1, "ACME"),
                LocalDate.of(2024, 6, 15),
                false,
                30);
        ov.setId(id);
        return ov;
    }

    // ── GET /overrides ────────────────────────────────────────────────────────

    @Nested @DisplayName("GET /overrides")
    class FindAll {

        @Test @DisplayName("200 with list of override DTOs")
        void ok() throws Exception {
            when(service.findAllByRoute(ROUTE_ID)).thenReturn(List.of(override(1), override(2)));

            mockMvc.perform(get(BASE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data[0].id").value(1))
                    .andExpect(jsonPath("$.data[0].overrideDate").value("2024-06-15"))
                    .andExpect(jsonPath("$.data[0].useTimeRanges").value(false))
                    .andExpect(jsonPath("$.data[0].baseDuration").value(30))
                    .andExpect(jsonPath("$.data[0].ranges").isArray());
        }

        @Test @DisplayName("200 with empty list when no overrides exist")
        void empty() throws Exception {
            when(service.findAllByRoute(ROUTE_ID)).thenReturn(List.of());

            mockMvc.perform(get(BASE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(0));
        }

        @Test @DisplayName("404 when route not found")
        void routeNotFound() throws Exception {
            when(service.findAllByRoute(ROUTE_ID))
                    .thenThrow(new ResourceNotFoundException("notFound.route|" + ROUTE_ID));

            mockMvc.perform(get(BASE)).andExpect(status().isNotFound());
        }
    }

    // ── GET /overrides/{id} ───────────────────────────────────────────────────

    @Nested @DisplayName("GET /overrides/{id}")
    class FindById {

        @Test @DisplayName("200 with single override DTO")
        void ok() throws Exception {
            when(service.findById(1)).thenReturn(override(1));

            mockMvc.perform(get(BASE + "/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.overrideDate").value("2024-06-15"))
                    .andExpect(jsonPath("$.data.baseDuration").value(30));
        }

        @Test @DisplayName("404 when override not found")
        void notFound() throws Exception {
            when(service.findById(99))
                    .thenThrow(new ResourceNotFoundException("notFound.routeCalendarOverride|99"));

            mockMvc.perform(get(BASE + "/99")).andExpect(status().isNotFound());
        }
    }

    // ── POST /overrides ───────────────────────────────────────────────────────

    @Nested @DisplayName("POST /overrides")
    class Save {

        @Test @DisplayName("201 with saved override DTO")
        void created() throws Exception {
            when(service.save(eq(ROUTE_ID), any())).thenReturn(override(1));

            mockMvc.perform(post(BASE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.overrideDate").value("2024-06-15"));
        }

        @Test @DisplayName("400 when required fields are missing")
        void validationFails() throws Exception {
            mockMvc.perform(post(BASE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test @DisplayName("404 when route not found")
        void routeNotFound() throws Exception {
            when(service.save(eq(ROUTE_ID), any()))
                    .thenThrow(new ResourceNotFoundException("notFound.route|" + ROUTE_ID));

            mockMvc.perform(post(BASE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY))
                    .andExpect(status().isNotFound());
        }
    }

    // ── DELETE /overrides/{id} ────────────────────────────────────────────────

    @Nested @DisplayName("DELETE /overrides/{id}")
    class Delete {

        @Test @DisplayName("200 when override deleted successfully")
        void ok() throws Exception {
            doNothing().when(service).delete(1);

            mockMvc.perform(delete(BASE + "/1"))
                    .andExpect(status().isOk());
            verify(service).delete(1);
        }

        @Test @DisplayName("404 when override not found")
        void notFound() throws Exception {
            doThrow(new ResourceNotFoundException("notFound.routeCalendarOverride|99"))
                    .when(service).delete(99);

            mockMvc.perform(delete(BASE + "/99")).andExpect(status().isNotFound());
        }
    }
}
