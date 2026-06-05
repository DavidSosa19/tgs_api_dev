package com.example.tgs_dev.controller;

import com.example.tgs_dev.controller.exception.ConflictException;
import com.example.tgs_dev.controller.exception.ConstraintMessageResolver;
import com.example.tgs_dev.controller.exception.GlobalExceptionHandler;
import com.example.tgs_dev.controller.exception.ResourceNotFoundException;
import com.example.tgs_dev.entity.Route;
import com.example.tgs_dev.entity.RouteOperationalPeriod;
import com.example.tgs_dev.service.RouteOperationalPeriodService;
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
@DisplayName("RouteOperationalPeriodController")
class RouteOperationalPeriodControllerTest {

    @Mock RouteOperationalPeriodService service;
    @Mock ConstraintMessageResolver     constraintResolver;

    MockMvc mockMvc;

    private static final Route ROUTE = route(10, "R-10");
    private static final String BASE  = "/api/routes/10/operational-periods";

    private static final LocalDate FROM = LocalDate.of(2024, 1, 15);
    private static final LocalDate TO   = LocalDate.of(2024, 11, 29);

    private static final String VALID_BODY = """
            {
              "label":                 "Año escolar 2024",
              "baseDuration":          30,
              "firstDeparture":        "06:00:00",
              "lastDeparture":         "22:00:00",
              "defaultHeadwayMinutes": 8,
              "effectiveFrom":         "2024-01-15",
              "effectiveTo":           "2024-11-29"
            }
            """;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new RouteOperationalPeriodController(service))
                .setControllerAdvice(new GlobalExceptionHandler(constraintResolver))
                .setValidator(validator)
                .setMessageConverters(new JacksonJsonHttpMessageConverter())
                .build();
    }

    private RouteOperationalPeriod period(int id) {
        return operationalPeriod(id, ROUTE, 30, 12, FROM, TO);
    }

    // ── GET /operational-periods ──────────────────────────────────────────────

    @Nested @DisplayName("GET /operational-periods")
    class FindAll {

        @Test @DisplayName("200 with list of period DTOs")
        void ok() throws Exception {
            when(service.findAllByRoute(10L)).thenReturn(List.of(period(1), period(2)));

            mockMvc.perform(get(BASE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data[0].id").value(1))
                    .andExpect(jsonPath("$.data[0].baseDuration").value(30))
                    .andExpect(jsonPath("$.data[0].defaultHeadwayMinutes").value(12))
                    .andExpect(jsonPath("$.data[0].effectiveFrom").value("2024-01-15"));
        }

        @Test @DisplayName("200 with empty list when route has no periods")
        void empty() throws Exception {
            when(service.findAllByRoute(10L)).thenReturn(List.of());
            mockMvc.perform(get(BASE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(0));
        }

        @Test @DisplayName("404 when route not found")
        void routeNotFound() throws Exception {
            when(service.findAllByRoute(10L))
                    .thenThrow(new ResourceNotFoundException("notFound.route|10"));
            mockMvc.perform(get(BASE)).andExpect(status().isNotFound());
        }
    }

    // ── GET /operational-periods/{id} ─────────────────────────────────────────

    @Nested @DisplayName("GET /operational-periods/{id}")
    class FindById {

        @Test @DisplayName("200 with single period DTO")
        void ok() throws Exception {
            when(service.findById(10L, 1)).thenReturn(period(1));

            mockMvc.perform(get(BASE + "/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(1));
        }

        @Test @DisplayName("404 when period not found")
        void notFound() throws Exception {
            when(service.findById(10L, 99))
                    .thenThrow(new ResourceNotFoundException("notFound.routeOperationalPeriod|99"));
            mockMvc.perform(get(BASE + "/99")).andExpect(status().isNotFound());
        }
    }

    // ── POST /operational-periods ─────────────────────────────────────────────

    @Nested @DisplayName("POST /operational-periods")
    class Create {

        @Test @DisplayName("201 with created period DTO")
        void created() throws Exception {
            when(service.create(eq(10L), any())).thenReturn(period(1));

            mockMvc.perform(post(BASE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.id").value(1));
        }

        @Test @DisplayName("409 when date range overlaps existing period")
        void conflict() throws Exception {
            when(service.create(eq(10L), any()))
                    .thenThrow(new ConflictException("conflict.routeOperationalPeriod.overlap"));

            mockMvc.perform(post(BASE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY))
                    .andExpect(status().isConflict());
        }

        @Test @DisplayName("400 when required fields are missing")
        void validationFails() throws Exception {
            mockMvc.perform(post(BASE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ── PUT /operational-periods/{id} ─────────────────────────────────────────

    @Nested @DisplayName("PUT /operational-periods/{id}")
    class Update {

        @Test @DisplayName("200 with updated period DTO")
        void ok() throws Exception {
            when(service.update(eq(10L), eq(1), any())).thenReturn(period(1));

            mockMvc.perform(put(BASE + "/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(1));
        }

        @Test @DisplayName("404 when period not found")
        void notFound() throws Exception {
            when(service.update(eq(10L), eq(99), any()))
                    .thenThrow(new ResourceNotFoundException("notFound.routeOperationalPeriod|99"));

            mockMvc.perform(put(BASE + "/99")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY))
                    .andExpect(status().isNotFound());
        }
    }

    // ── DELETE /operational-periods/{id} ──────────────────────────────────────

    @Nested @DisplayName("DELETE /operational-periods/{id}")
    class Delete {

        @Test @DisplayName("200 when period deleted successfully")
        void ok() throws Exception {
            doNothing().when(service).delete(10L, 1);

            mockMvc.perform(delete(BASE + "/1"))
                    .andExpect(status().isOk());
            verify(service).delete(10L, 1);
        }

        @Test @DisplayName("404 when period not found")
        void notFound() throws Exception {
            doThrow(new ResourceNotFoundException("notFound.routeOperationalPeriod|99"))
                    .when(service).delete(10L, 99);

            mockMvc.perform(delete(BASE + "/99"))
                    .andExpect(status().isNotFound());
        }
    }
}
