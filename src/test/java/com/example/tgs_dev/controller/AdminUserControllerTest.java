package com.example.tgs_dev.controller;

import com.example.tgs_dev.controller.admin.AdminUserController;
import com.example.tgs_dev.controller.exception.BusinessException;
import com.example.tgs_dev.controller.exception.ConstraintMessageResolver;
import com.example.tgs_dev.controller.exception.GlobalExceptionHandler;
import com.example.tgs_dev.controller.exception.ResourceNotFoundException;
import com.example.tgs_dev.controller.response.admin.UserAdminDTO;
import com.example.tgs_dev.service.admin.AdminUserService;
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
@DisplayName("AdminUserController")
class AdminUserControllerTest {

    @Mock AdminUserService adminUserService;
    @Mock ConstraintMessageResolver constraintResolver;

    MockMvc mockMvc;
    static final String BASE = "/api/admin/users";

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AdminUserController(adminUserService))
                .setControllerAdvice(new GlobalExceptionHandler(constraintResolver))
                .setValidator(validator)
                .setMessageConverters(new JacksonJsonHttpMessageConverter())
                .build();
    }

    private UserAdminDTO dto(int id) {
        return new UserAdminDTO((long) id, "user" + id, List.of("USER"),
                true, 1, "Company 1", null);
    }

    private UserAdminDTO inactiveDto(int id) {
        return new UserAdminDTO((long) id, "user" + id, List.of("USER"),
                false, 1, "Company 1", null);
    }

    // ── GET / ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /")
    class GetAll {
        @Test
        @DisplayName("200 with user list including inactive")
        void ok() throws Exception {
            when(adminUserService.findAll()).thenReturn(List.of(dto(1), inactiveDto(2)));
            mockMvc.perform(get(BASE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(2));
        }
    }

    // ── GET /company/{companyId} ───────────────────────────────────────────────

    @Nested
    @DisplayName("GET /company/{companyId}")
    class GetByCompany {
        @Test
        @DisplayName("200 with filtered user list")
        void ok() throws Exception {
            when(adminUserService.findByCompany(1)).thenReturn(List.of(dto(1)));
            mockMvc.perform(get(BASE + "/company/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1));
        }
    }

    // ── GET /{id} ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /{id}")
    class GetById {
        @Test
        @DisplayName("200 when found")
        void found() throws Exception {
            when(adminUserService.findById(1L)).thenReturn(dto(1));
            mockMvc.perform(get(BASE + "/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(1));
        }

        @Test
        @DisplayName("200 when found and inactive")
        void foundInactive() throws Exception {
            when(adminUserService.findById(2L)).thenReturn(inactiveDto(2));
            mockMvc.perform(get(BASE + "/2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.active").value(false));
        }

        @Test
        @DisplayName("404 when not found")
        void notFound() throws Exception {
            when(adminUserService.findById(99L))
                    .thenThrow(new ResourceNotFoundException("notFound.user|99"));
            mockMvc.perform(get(BASE + "/99"))
                    .andExpect(status().isNotFound());
        }
    }

    // ── POST / ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /")
    class Create {
        @Test
        @DisplayName("201 with valid body (inline person)")
        void created() throws Exception {
            when(adminUserService.create(any())).thenReturn(dto(10));
            mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                  "companyId": 1,
                                  "userName": "newuser",
                                  "password": "secret123",
                                  "roleId": 1,
                                  "documentNumber": "12345",
                                  "firstName": "John",
                                  "firstLastName": "Doe"
                                }"""))
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

        @Test
        @DisplayName("422 when SUPER_ADMIN role is assigned")
        void superAdminForbidden() throws Exception {
            when(adminUserService.create(any()))
                    .thenThrow(new BusinessException("admin.role.superAdminForbidden"));
            mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                  "companyId": 1,
                                  "userName": "newuser",
                                  "password": "secret123",
                                  "roleId": 99,
                                  "documentNumber": "12345",
                                  "firstName": "John",
                                  "firstLastName": "Doe"
                                }"""))
                    .andExpect(status().is(422));
        }

        @Test
        @DisplayName("422 when person already has a user")
        void personAlreadyHasUser() throws Exception {
            when(adminUserService.create(any()))
                    .thenThrow(new BusinessException("admin.person.alreadyHasUser"));
            mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                  "companyId": 1,
                                  "userName": "newuser",
                                  "password": "secret123",
                                  "roleId": 1,
                                  "personId": 5
                                }"""))
                    .andExpect(status().is(422));
        }
    }

    // ── PUT /{id} ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("PUT /{id}")
    class Update {
        @Test
        @DisplayName("200 with updated user (no password change)")
        void updated() throws Exception {
            when(adminUserService.update(eq(1L), any())).thenReturn(dto(1));
            mockMvc.perform(put(BASE + "/1").contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"roleId":1,"active":true}"""))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("200 with updated user and new password")
        void updatedWithPassword() throws Exception {
            when(adminUserService.update(eq(1L), any())).thenReturn(dto(1));
            mockMvc.perform(put(BASE + "/1").contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"roleId":1,"active":true,"newPassword":"newpass123"}"""))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("400 when required fields missing")
        void validationFails() throws Exception {
            mockMvc.perform(put(BASE + "/1").contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("400 when new password too short")
        void passwordTooShort() throws Exception {
            mockMvc.perform(put(BASE + "/1").contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"roleId":1,"active":true,"newPassword":"short"}"""))
                    .andExpect(status().isBadRequest());
        }
    }

    // ── DELETE /{id} ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("DELETE /{id}")
    class Deactivate {
        @Test
        @DisplayName("200 when deactivated")
        void deactivated() throws Exception {
            doNothing().when(adminUserService).deactivate(1L);
            mockMvc.perform(delete(BASE + "/1"))
                    .andExpect(status().isOk());
            verify(adminUserService).deactivate(1L);
        }

        @Test
        @DisplayName("404 when user not found")
        void notFound() throws Exception {
            doThrow(new ResourceNotFoundException("notFound.user|99"))
                    .when(adminUserService).deactivate(99L);
            mockMvc.perform(delete(BASE + "/99"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("403 when service throws AccessDeniedException")
        void accessDenied() throws Exception {
            doThrow(new AccessDeniedException("admin.access.denied"))
                    .when(adminUserService).deactivate(1L);
            mockMvc.perform(delete(BASE + "/1"))
                    .andExpect(status().isForbidden());
        }
    }

    // ── PATCH /{id}/reactivate ────────────────────────────────────────────────

    @Nested
    @DisplayName("PATCH /{id}/reactivate")
    class Reactivate {
        @Test
        @DisplayName("200 when reactivated")
        void reactivated() throws Exception {
            doNothing().when(adminUserService).reactivate(2L);
            mockMvc.perform(patch(BASE + "/2/reactivate"))
                    .andExpect(status().isOk());
            verify(adminUserService).reactivate(2L);
        }

        @Test
        @DisplayName("404 when user not found")
        void notFound() throws Exception {
            doThrow(new ResourceNotFoundException("notFound.user|99"))
                    .when(adminUserService).reactivate(99L);
            mockMvc.perform(patch(BASE + "/99/reactivate"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("403 when service throws AccessDeniedException")
        void accessDenied() throws Exception {
            doThrow(new AccessDeniedException("admin.access.denied"))
                    .when(adminUserService).reactivate(1L);
            mockMvc.perform(patch(BASE + "/1/reactivate"))
                    .andExpect(status().isForbidden());
        }
    }
}
