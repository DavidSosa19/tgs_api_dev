package com.example.tgs_dev.controller;

import com.example.tgs_dev.controller.exception.ConstraintMessageResolver;
import com.example.tgs_dev.controller.exception.GlobalExceptionHandler;
import com.example.tgs_dev.controller.request.ScheduleTemplateRequest;
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

import static org.mockito.ArgumentMatchers.*;
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

    private ScheduleTemplateDTO dto(int id, long groupId) {
        RouteDTO routeDTO = new RouteDTO(id, (long) id, "R-" + id, true);
        return new ScheduleTemplateDTO(id, groupId, "T-" + id, "Template " + id, true, LocalTime.of(6, 0), routeDTO, null);
    }

    @Nested @DisplayName("GET /")
    class GetAll {
        @Test @DisplayName("200 with template list")
        void ok() throws Exception {
            when(scheduleTemplateService.findAll()).thenReturn(List.of(template(1)));
            when(scheduleTemplateMapper.toDTOList(any())).thenReturn(List.of(dto(1, 50L)));
            mockMvc.perform(get(BASE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray());
        }
    }

    @Nested @DisplayName("GET /{groupId}")
    class GetById {
        @Test @DisplayName("200 when found")
        void found() throws Exception {
            when(scheduleTemplateService.findByGroupId(50L)).thenReturn(template(1));
            when(scheduleTemplateMapper.toDTO(any(ScheduleTemplate.class))).thenReturn(dto(1, 50L));
            mockMvc.perform(get(BASE + "/50"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.groupId").value(50));
        }
    }

    @Nested @DisplayName("POST /")
    class Create {
        @Test @DisplayName("201 without secondary route — resolves primary route by group id")
        void createdNoSecondary() throws Exception {
            Route r = new Route("R"); r.setId(1);
            when(routeService.findByGroupId(100L)).thenReturn(r);
            when(scheduleTemplateService.create(any(ScheduleTemplateRequest.class), eq(r), isNull()))
                    .thenReturn(template(1));
            when(scheduleTemplateMapper.toDTO(any(ScheduleTemplate.class))).thenReturn(dto(1, 50L));
            mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"routeId":100,"templateNumber":"T-1","name":"Template 1","startTime":"06:00:00"}"""))
                    .andExpect(status().isCreated());
        }

        @Test @DisplayName("201 with secondaryRouteId resolves both routes by group id")
        void createdWithSecondary() throws Exception {
            Route r1 = new Route("R1"); r1.setId(1);
            Route r2 = new Route("R2"); r2.setId(2);
            when(routeService.findByGroupId(100L)).thenReturn(r1);
            when(routeService.findByGroupId(200L)).thenReturn(r2);
            when(scheduleTemplateService.create(any(ScheduleTemplateRequest.class), eq(r1), eq(r2)))
                    .thenReturn(template(1));
            when(scheduleTemplateMapper.toDTO(any(ScheduleTemplate.class))).thenReturn(dto(1, 50L));
            mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"routeId":100,"secondaryRouteId":200,"templateNumber":"T-1","name":"Template 1","startTime":"06:00:00"}"""))
                    .andExpect(status().isCreated());
            verify(routeService).findByGroupId(200L);
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
        @Test @DisplayName("200 with updated template")
        void updated() throws Exception {
            Route r = new Route("R"); r.setId(1);
            when(routeService.findByGroupId(100L)).thenReturn(r);
            when(scheduleTemplateService.update(eq(50L), any(ScheduleTemplateRequest.class), eq(r), isNull()))
                    .thenReturn(template(1));
            when(scheduleTemplateMapper.toDTO(any(ScheduleTemplate.class))).thenReturn(dto(1, 50L));
            mockMvc.perform(put(BASE + "/50").contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"routeId":100,"templateNumber":"T-1","name":"Template 1","startTime":"06:00:00"}"""))
                    .andExpect(status().isOk());
        }
    }

    @Nested @DisplayName("DELETE /{groupId}")
    class Delete {
        @Test @DisplayName("200 when deactivated")
        void deleted() throws Exception {
            mockMvc.perform(delete(BASE + "/50"))
                    .andExpect(status().isOk());
            verify(scheduleTemplateService).deactivate(50L);
        }
    }

    @Nested @DisplayName("PATCH /{groupId}/reactivate")
    class Reactivate {
        @Test @DisplayName("200 when reactivated")
        void reactivated() throws Exception {
            when(scheduleTemplateService.reactivate(50L)).thenReturn(template(1));
            mockMvc.perform(patch(BASE + "/50/reactivate"))
                    .andExpect(status().isOk());
            verify(scheduleTemplateService).reactivate(50L);
        }
    }

    @Nested @DisplayName("POST /filter")
    class Filter {
        @Test @DisplayName("200 with page result")
        void ok() throws Exception {
            when(scheduleTemplateService.filter(any())).thenReturn(new PageImpl<>(List.of(template(1)), PageRequest.of(0, 10), 1));
            when(scheduleTemplateMapper.toDTO(any(ScheduleTemplate.class))).thenReturn(dto(1, 50L));
            mockMvc.perform(post(BASE + "/filter").contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"page":0,"size":10}"""))
                    .andExpect(status().isOk());
        }
    }
}
