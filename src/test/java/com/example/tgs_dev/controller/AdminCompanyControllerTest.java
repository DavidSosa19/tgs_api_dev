package com.example.tgs_dev.controller;

import com.example.tgs_dev.controller.admin.AdminCompanyController;
import com.example.tgs_dev.controller.exception.ConstraintMessageResolver;
import com.example.tgs_dev.controller.exception.GlobalExceptionHandler;
import com.example.tgs_dev.controller.exception.ResourceNotFoundException;
import com.example.tgs_dev.controller.response.admin.CompanyAdminDTO;
import com.example.tgs_dev.service.admin.AdminCompanyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminCompanyController")
class AdminCompanyControllerTest {

    @Mock AdminCompanyService adminCompanyService;
    @Mock ConstraintMessageResolver constraintResolver;

    MockMvc mockMvc;
    static final String BASE = "/api/admin/companies";

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AdminCompanyController(adminCompanyService))
                .setControllerAdvice(new GlobalExceptionHandler(constraintResolver))
                .setValidator(validator)
                .setMessageConverters(new JacksonJsonHttpMessageConverter())
                .build();
    }

    private CompanyAdminDTO dto(int id) {
        return new CompanyAdminDTO(id, "Company " + id, "NIT-" + id, true);
    }

    private CompanyAdminDTO inactiveDto(int id) {
        return new CompanyAdminDTO(id, "Company " + id, "NIT-" + id, false);
    }

    @Nested
    @DisplayName("GET /")
    class GetAll {
        @Test
        @DisplayName("200 with company list including inactive")
        void ok() throws Exception {
            when(adminCompanyService.findAll()).thenReturn(List.of(dto(1), inactiveDto(2)));
            mockMvc.perform(get(BASE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(2));
        }
    }

    @Nested
    @DisplayName("GET /{id}")
    class GetById {
        @Test
        @DisplayName("200 when found")
        void found() throws Exception {
            when(adminCompanyService.findById(1)).thenReturn(dto(1));
            mockMvc.perform(get(BASE + "/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(1));
        }

        @Test
        @DisplayName("200 when found and inactive")
        void foundInactive() throws Exception {
            when(adminCompanyService.findById(2)).thenReturn(inactiveDto(2));
            mockMvc.perform(get(BASE + "/2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.active").value(false));
        }

        @Test
        @DisplayName("404 when not found")
        void notFound() throws Exception {
            when(adminCompanyService.findById(99))
                    .thenThrow(new ResourceNotFoundException("notFound.company|99"));
            mockMvc.perform(get(BASE + "/99"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("POST /")
    class Create {
        @Test
        @DisplayName("201 with valid body")
        void created() throws Exception {
            when(adminCompanyService.create(any())).thenReturn(dto(10));
            mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"name":"Acme Corp","nit":"900-001"}"""))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.id").value(10));
        }

        @Test
        @DisplayName("400 when required fields missing")
        void validationFails() throws Exception {
            mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("PUT /{id}")
    class Update {
        @Test
        @DisplayName("200 with updated company")
        void updated() throws Exception {
            when(adminCompanyService.update(eq(1), any())).thenReturn(dto(1));
            mockMvc.perform(put(BASE + "/1").contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"name":"Updated Corp","nit":"900-002"}"""))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(1));
        }

        @Test
        @DisplayName("404 when company not found")
        void notFound() throws Exception {
            when(adminCompanyService.update(eq(99), any()))
                    .thenThrow(new ResourceNotFoundException("notFound.company|99"));
            mockMvc.perform(put(BASE + "/99").contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"name":"X","nit":"Y"}"""))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("DELETE /{id}")
    class Deactivate {
        @Test
        @DisplayName("200 when deactivated")
        void deactivated() throws Exception {
            doNothing().when(adminCompanyService).deactivate(1);
            mockMvc.perform(delete(BASE + "/1"))
                    .andExpect(status().isOk());
            verify(adminCompanyService).deactivate(1);
        }

        @Test
        @DisplayName("404 when company not found")
        void notFound() throws Exception {
            doThrow(new ResourceNotFoundException("notFound.company|99"))
                    .when(adminCompanyService).deactivate(99);
            mockMvc.perform(delete(BASE + "/99"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("403 when service throws AccessDeniedException")
        void accessDenied() throws Exception {
            doThrow(new AccessDeniedException("admin.access.denied"))
                    .when(adminCompanyService).deactivate(1);
            mockMvc.perform(delete(BASE + "/1"))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("PATCH /{id}/reactivate")
    class Reactivate {
        @Test
        @DisplayName("200 when reactivated")
        void reactivated() throws Exception {
            doNothing().when(adminCompanyService).reactivate(2);
            mockMvc.perform(patch(BASE + "/2/reactivate"))
                    .andExpect(status().isOk());
            verify(adminCompanyService).reactivate(2);
        }

        @Test
        @DisplayName("404 when company not found")
        void notFound() throws Exception {
            doThrow(new ResourceNotFoundException("notFound.company|99"))
                    .when(adminCompanyService).reactivate(99);
            mockMvc.perform(patch(BASE + "/99/reactivate"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("403 when service throws AccessDeniedException")
        void accessDenied() throws Exception {
            doThrow(new AccessDeniedException("admin.access.denied"))
                    .when(adminCompanyService).reactivate(1);
            mockMvc.perform(patch(BASE + "/1/reactivate"))
                    .andExpect(status().isForbidden());
        }
    }
}
