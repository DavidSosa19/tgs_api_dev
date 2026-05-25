package com.example.tgs_dev.controller;

import com.example.tgs_dev.controller.exception.ConstraintMessageResolver;
import com.example.tgs_dev.controller.exception.GlobalExceptionHandler;
import com.example.tgs_dev.controller.response.RouteDTO;
import com.example.tgs_dev.entity.Route;
import com.example.tgs_dev.mapper.RouteMapper;
import com.example.tgs_dev.service.RouteService;
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
@DisplayName("RouteController")
class RouteControllerTest {

    @Mock RouteService routeService;
    @Mock RouteMapper  routeMapper;
    @Mock ConstraintMessageResolver constraintResolver;

    MockMvc mockMvc;
    static final String BASE = "/api/route";

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new RouteController(routeService, routeMapper))
                .setControllerAdvice(new GlobalExceptionHandler(constraintResolver))
                .setValidator(validator)
                .setMessageConverters(new JacksonJsonHttpMessageConverter())
                .build();
    }

    private Route route(int id) {
        Route r = new Route("R-" + id);
        r.setId(id);
        return r;
    }

    private RouteDTO dto(int id) {
        return new RouteDTO(id, "R-" + id, true);
    }

    @Nested @DisplayName("GET /")
    class GetAll {
        @Test @DisplayName("200 with route list")
        void ok() throws Exception {
            when(routeService.findAll()).thenReturn(List.of(route(1)));
            when(routeMapper.toDTOList(any())).thenReturn(List.of(dto(1)));
            mockMvc.perform(get(BASE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray());
        }
    }

    @Nested @DisplayName("GET /{id}")
    class GetById {
        @Test @DisplayName("200 when found")
        void found() throws Exception {
            when(routeService.findById(1)).thenReturn(route(1));
            when(routeMapper.toDTO(any(Route.class))).thenReturn(dto(1));
            mockMvc.perform(get(BASE + "/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(1));
        }

        @Test @DisplayName("404 when not found")
        void notFound() throws Exception {
            when(routeService.findById(99)).thenThrow(new NoSuchElementException("notFound.route|99"));
            mockMvc.perform(get(BASE + "/99"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested @DisplayName("POST /")
    class Create {
        @Test @DisplayName("201 with valid body")
        void created() throws Exception {
            Route r = route(1);
            when(routeMapper.toEntity(any())).thenReturn(r);
            when(routeService.save(r)).thenReturn(r);
            when(routeMapper.toDTO(r)).thenReturn(dto(1));
            mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"routeNumber":"R-1"}"""))
                    .andExpect(status().isCreated());
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
        @Test @DisplayName("200 with updated route")
        void updated() throws Exception {
            Route r = route(1);
            when(routeService.findById(1)).thenReturn(r);
            when(routeService.save(r)).thenReturn(r);
            when(routeMapper.toDTO(r)).thenReturn(dto(1));
            mockMvc.perform(put(BASE + "/1").contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"routeNumber":"R-1"}"""))
                    .andExpect(status().isOk());
        }
    }

    @Nested @DisplayName("DELETE /{id}")
    class Delete {
        @Test @DisplayName("200 when deleted")
        void deleted() throws Exception {
            Route r = route(1);
            when(routeService.findById(1)).thenReturn(r);
            mockMvc.perform(delete(BASE + "/1"))
                    .andExpect(status().isOk());
            verify(routeService).delete(r);
        }

        @Test @DisplayName("404 when not found")
        void notFound() throws Exception {
            when(routeService.findById(99)).thenThrow(new NoSuchElementException("notFound.route|99"));
            mockMvc.perform(delete(BASE + "/99"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested @DisplayName("POST /filter")
    class Filter {
        @Test @DisplayName("200 with page result")
        void ok() throws Exception {
            when(routeService.filter(any())).thenReturn(new PageImpl<>(List.of(route(1)), PageRequest.of(0, 10), 1));
            when(routeMapper.toDTO(any(Route.class))).thenReturn(dto(1));
            mockMvc.perform(post(BASE + "/filter").contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"page":0,"size":10}"""))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content").isArray());
        }
    }
}
