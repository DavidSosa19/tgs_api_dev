package com.example.tgs_dev.controller;

import com.example.tgs_dev.controller.exception.ConstraintMessageResolver;
import com.example.tgs_dev.controller.exception.GlobalExceptionHandler;
import com.example.tgs_dev.controller.response.RouteDTO;
import com.example.tgs_dev.controller.response.ScheduleTemplateDTO;
import com.example.tgs_dev.entity.Route;
import com.example.tgs_dev.entity.ScheduleTemplate;
import com.example.tgs_dev.mapper.ScheduleTemplateMapper;
import com.example.tgs_dev.service.RouteService;
import com.example.tgs_dev.service.ScheduleTemplateService;
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

import java.time.LocalTime;
import java.util.List;
import java.util.NoSuchElementException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScheduleTemplateController")
class ScheduleTemplateControllerTest {

    @Mock ScheduleTemplateService scheduleTemplateService;
    @Mock RouteService            routeService;
    @Mock ScheduleTemplateMapper  scheduleTemplateMapper;
    @Mock ConstraintMessageResolver constraintResolver;

    MockMvc mockMvc;
    static final String BASE = "/api/scheduleTemplate";

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ScheduleTemplateController(scheduleTemplateService, routeService, scheduleTemplateMapper))
                .setControllerAdvice(new GlobalExceptionHandler(constraintResolver))
                .setValidator(validator)
                .setMessageConverters(new JacksonJsonHttpMessageConverter())
                .build();
    }

    private ScheduleTemplate template(int id) {
        Route r = new Route("R-" + id);
        r.setId(id);
        ScheduleTemplate t = new ScheduleTemplate(r, "T-" + id, "Template " + id, LocalTime.of(6, 0));
        t.setId(id);
        return t;
    }

    private ScheduleTemplateDTO dto(int id) {
        RouteDTO routeDTO = new RouteDTO(id, "R-" + id, true);
        return new ScheduleTemplateDTO(id, "T-" + id, "Template " + id, true, LocalTime.of(6, 0), routeDTO, null);
    }

    @Nested @DisplayName("GET /")
    class GetAll {
        @Test @DisplayName("200 with template list")
        void ok() throws Exception {
            when(scheduleTemplateService.findAll()).thenReturn(List.of(template(1)));
            when(scheduleTemplateMapper.toDTOList(any())).thenReturn(List.of(dto(1)));
            mockMvc.perform(get(BASE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray());
        }
    }

    @Nested @DisplayName("GET /{id}")
    class GetById {
        @Test @DisplayName("200 when found")
        void found() throws Exception {
            when(scheduleTemplateService.findById(1)).thenReturn(template(1));
            when(scheduleTemplateMapper.toDTO(any(ScheduleTemplate.class))).thenReturn(dto(1));
            mockMvc.perform(get(BASE + "/1"))
                    .andExpect(status().isOk());
        }

        @Test @DisplayName("404 when not found")
        void notFound() throws Exception {
            when(scheduleTemplateService.findById(99)).thenThrow(new NoSuchElementException("notFound.scheduleTemplate|99"));
            mockMvc.perform(get(BASE + "/99"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested @DisplayName("POST /")
    class Create {
        @Test @DisplayName("201 without secondary route")
        void createdNoSecondary() throws Exception {
            Route r = new Route("");
            r.setId(1);
            ScheduleTemplate t = template(1);
            when(routeService.findById(1)).thenReturn(r);
            when(scheduleTemplateService.save(any())).thenReturn(t);
            when(scheduleTemplateMapper.toDTO(t)).thenReturn(dto(1));
            mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"routeId":1,"templateNumber":"T-1","name":"Template 1","startTime":"06:00:00"}"""))
                    .andExpect(status().isCreated());
        }

        @Test @DisplayName("201 with secondaryRouteId resolves secondary route")
        void createdWithSecondary() throws Exception {
            Route r1 = new Route(""); r1.setId(1);
            Route r2 = new Route(""); r2.setId(2);
            ScheduleTemplate t = template(1);
            when(routeService.findById(1)).thenReturn(r1);
            when(routeService.findById(2)).thenReturn(r2);
            when(scheduleTemplateService.save(any())).thenReturn(t);
            when(scheduleTemplateMapper.toDTO(t)).thenReturn(dto(1));
            mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"routeId":1,"secondaryRouteId":2,"templateNumber":"T-1","name":"Template 1","startTime":"06:00:00"}"""))
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
        @Test @DisplayName("200 with updated template (no secondary route)")
        void updated() throws Exception {
            Route r = new Route(""); r.setId(1);
            ScheduleTemplate t = template(1);
            when(scheduleTemplateService.findById(1)).thenReturn(t);
            when(routeService.findById(1)).thenReturn(r);
            when(scheduleTemplateService.save(t)).thenReturn(t);
            when(scheduleTemplateMapper.toDTO(t)).thenReturn(dto(1));
            mockMvc.perform(put(BASE + "/1").contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"routeId":1,"templateNumber":"T-1","name":"Template 1","startTime":"06:00:00"}"""))
                    .andExpect(status().isOk());
            verify(routeService, times(1)).findById(1);
        }

        @Test @DisplayName("200 with secondaryRouteId resolves secondary route on update")
        void updatedWithSecondaryRoute() throws Exception {
            Route r1 = new Route(""); r1.setId(1);
            Route r2 = new Route(""); r2.setId(2);
            ScheduleTemplate t = template(1);
            when(scheduleTemplateService.findById(1)).thenReturn(t);
            when(routeService.findById(1)).thenReturn(r1);
            when(routeService.findById(2)).thenReturn(r2);
            when(scheduleTemplateService.save(t)).thenReturn(t);
            when(scheduleTemplateMapper.toDTO(t)).thenReturn(dto(1));
            mockMvc.perform(put(BASE + "/1").contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"routeId":1,"secondaryRouteId":2,"templateNumber":"T-1","name":"Template 1","startTime":"06:00:00"}"""))
                    .andExpect(status().isOk());
            verify(routeService).findById(2);
        }
    }

    @Nested @DisplayName("DELETE /{id}")
    class Delete {
        @Test @DisplayName("200 when deleted")
        void deleted() throws Exception {
            ScheduleTemplate t = template(1);
            when(scheduleTemplateService.findById(1)).thenReturn(t);
            mockMvc.perform(delete(BASE + "/1"))
                    .andExpect(status().isOk());
            verify(scheduleTemplateService).delete(t);
        }

        @Test @DisplayName("404 when not found")
        void notFound() throws Exception {
            when(scheduleTemplateService.findById(99)).thenThrow(new NoSuchElementException("notFound.scheduleTemplate|99"));
            mockMvc.perform(delete(BASE + "/99"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested @DisplayName("POST /filter")
    class Filter {
        @Test @DisplayName("200 with page result")
        void ok() throws Exception {
            when(scheduleTemplateService.filter(any())).thenReturn(new PageImpl<>(List.of(template(1)), PageRequest.of(0, 10), 1));
            when(scheduleTemplateMapper.toDTO(any(ScheduleTemplate.class))).thenReturn(dto(1));
            mockMvc.perform(post(BASE + "/filter").contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"page":0,"size":10}"""))
                    .andExpect(status().isOk());
        }
    }
}
