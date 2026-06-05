package com.example.tgs_dev.controller;

import com.example.tgs_dev.controller.exception.ConstraintMessageResolver;
import com.example.tgs_dev.controller.exception.GlobalExceptionHandler;
import com.example.tgs_dev.controller.exception.ResourceNotFoundException;
import com.example.tgs_dev.entity.SeasonalPattern;
import com.example.tgs_dev.service.SeasonalPatternService;
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
@DisplayName("SeasonalPatternController")
class SeasonalPatternControllerTest {

    @Mock SeasonalPatternService service;
    @Mock ConstraintMessageResolver constraintResolver;

    MockMvc mockMvc;

    private static final int    ROUTE_ID = 10;
    private static final String BASE     = "/api/routes/10/seasonal-patterns";

    private static final LocalDate FROM = LocalDate.of(2024, 6,  1);
    private static final LocalDate TO   = LocalDate.of(2024, 8, 31);

    private static final String VALID_BODY = """
            {
              "name":          "Summer 2024",
              "seasonFrom":    "2024-06-01",
              "seasonTo":      "2024-08-31",
              "useTimeRanges": false,
              "baseDuration":  45
            }
            """;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new SeasonalPatternController(service))
                .setControllerAdvice(new GlobalExceptionHandler(constraintResolver))
                .setValidator(validator)
                .setMessageConverters(new JacksonJsonHttpMessageConverter())
                .build();
    }

    private SeasonalPattern pattern(int id) {
        SeasonalPattern sp = new SeasonalPattern(
                route(ROUTE_ID, "R-10"),
                company(1, "ACME"),
                "Summer 2024",
                FROM,
                TO,
                false,
                45);
        sp.setId(id);
        return sp;
    }

    // ── GET /seasonal-patterns ────────────────────────────────────────────────

    @Nested @DisplayName("GET /seasonal-patterns")
    class FindAll {

        @Test @DisplayName("200 with list of pattern DTOs")
        void ok() throws Exception {
            when(service.findAllByRoute(ROUTE_ID)).thenReturn(List.of(pattern(1), pattern(2)));

            mockMvc.perform(get(BASE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data[0].id").value(1))
                    .andExpect(jsonPath("$.data[0].name").value("Summer 2024"))
                    .andExpect(jsonPath("$.data[0].seasonFrom").value("2024-06-01"))
                    .andExpect(jsonPath("$.data[0].seasonTo").value("2024-08-31"))
                    .andExpect(jsonPath("$.data[0].useTimeRanges").value(false))
                    .andExpect(jsonPath("$.data[0].baseDuration").value(45))
                    .andExpect(jsonPath("$.data[0].ranges").isArray());
        }

        @Test @DisplayName("200 with empty list when route has no patterns")
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

    // ── GET /seasonal-patterns/{id} ───────────────────────────────────────────

    @Nested @DisplayName("GET /seasonal-patterns/{id}")
    class FindById {

        @Test @DisplayName("200 with single pattern DTO")
        void ok() throws Exception {
            when(service.findById(1)).thenReturn(pattern(1));

            mockMvc.perform(get(BASE + "/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.name").value("Summer 2024"))
                    .andExpect(jsonPath("$.data.baseDuration").value(45));
        }

        @Test @DisplayName("404 when pattern not found")
        void notFound() throws Exception {
            when(service.findById(99))
                    .thenThrow(new ResourceNotFoundException("notFound.seasonalPattern|99"));

            mockMvc.perform(get(BASE + "/99")).andExpect(status().isNotFound());
        }
    }

    // ── POST /seasonal-patterns ───────────────────────────────────────────────

    @Nested @DisplayName("POST /seasonal-patterns")
    class Create {

        @Test @DisplayName("201 with created pattern DTO")
        void created() throws Exception {
            when(service.save(eq(ROUTE_ID), any())).thenReturn(pattern(1));

            mockMvc.perform(post(BASE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.name").value("Summer 2024"));
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

    // ── PUT /seasonal-patterns/{id} ───────────────────────────────────────────

    @Nested @DisplayName("PUT /seasonal-patterns/{id}")
    class Update {

        @Test @DisplayName("200 with updated pattern DTO")
        void ok() throws Exception {
            when(service.update(eq(1), any())).thenReturn(pattern(1));

            mockMvc.perform(put(BASE + "/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.name").value("Summer 2024"));
        }

        @Test @DisplayName("404 when pattern not found")
        void notFound() throws Exception {
            when(service.update(eq(99), any()))
                    .thenThrow(new ResourceNotFoundException("notFound.seasonalPattern|99"));

            mockMvc.perform(put(BASE + "/99")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY))
                    .andExpect(status().isNotFound());
        }

        @Test @DisplayName("400 when required fields are missing")
        void validationFails() throws Exception {
            mockMvc.perform(put(BASE + "/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ── DELETE /seasonal-patterns/{id} ────────────────────────────────────────

    @Nested @DisplayName("DELETE /seasonal-patterns/{id}")
    class Delete {

        @Test @DisplayName("200 when pattern deleted successfully")
        void ok() throws Exception {
            doNothing().when(service).delete(1);

            mockMvc.perform(delete(BASE + "/1"))
                    .andExpect(status().isOk());
            verify(service).delete(1);
        }

        @Test @DisplayName("404 when pattern not found")
        void notFound() throws Exception {
            doThrow(new ResourceNotFoundException("notFound.seasonalPattern|99"))
                    .when(service).delete(99);

            mockMvc.perform(delete(BASE + "/99")).andExpect(status().isNotFound());
        }
    }
}
