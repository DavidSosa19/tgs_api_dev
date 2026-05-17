package com.example.tgs_dev.controller;

import com.example.tgs_dev.controller.response.CompanyContextDTO;
import com.example.tgs_dev.controller.response.UserContextDTO;
import com.example.tgs_dev.entity.Company;
import com.example.tgs_dev.entity.User;
import com.example.tgs_dev.mapper.UserContextMapper;
import com.example.tgs_dev.mapper.UserMapper;
import com.example.tgs_dev.security.JwtService;
import com.example.tgs_dev.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthController")
class AuthControllerTest {

    @Mock AuthenticationManager authManager;
    @Mock JwtService            jwtService;
    @Mock UserDetailsService    userDetailsService;
    @Mock UserService           userService;
    @Mock UserMapper            userMapper;
    @Mock UserContextMapper     userContextMapper;
    @InjectMocks AuthController sut;

    private User userWithCompany() {
        Company company = new Company("ACME", "NIT-1");
        company.setId(1);
        User user = User.builder()
                .userName("jdoe")
                .password("pw")
                .roles(new HashSet<>())
                .company(company)
                .build();
        user.setId(1L);
        return user;
    }

    @Nested
    @DisplayName("GET /api/auth/me")
    class Me {

        @Test
        @DisplayName("retorna 200 con el contexto del usuario autenticado")
        void returnsUserContext() {
            User user = userWithCompany();
            UserContextDTO expectedDTO = new UserContextDTO(
                    1L, "jdoe", List.of("USER"), List.of("ROUTE_READ"),
                    new CompanyContextDTO(1, "ACME", "NIT-1"), true);

            Authentication auth = new UsernamePasswordAuthenticationToken(user, null, List.of());
            when(userContextMapper.toDTO(user)).thenReturn(expectedDTO);

            var response = sut.me(auth);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().success()).isTrue();
            assertThat(response.getBody().data()).isSameAs(expectedDTO);
        }

        @Test
        @DisplayName("el DTO devuelto incluye empresa y permisos")
        void dtoContainsCompanyAndPermissions() {
            User user = userWithCompany();
            UserContextDTO expectedDTO = new UserContextDTO(
                    1L, "jdoe",
                    List.of("ADMIN"),
                    List.of("PERSON_READ", "PERSON_WRITE", "ROUTE_READ", "ROUTE_WRITE"),
                    new CompanyContextDTO(1, "ACME", "NIT-1"),
                    true);

            Authentication auth = mock(Authentication.class);
            when(auth.getPrincipal()).thenReturn(user);
            when(userContextMapper.toDTO(user)).thenReturn(expectedDTO);

            var response = sut.me(auth);

            UserContextDTO dto = response.getBody().data();
            assertThat(dto.company().name()).isEqualTo("ACME");
            assertThat(dto.permissions()).contains("PERSON_READ", "ROUTE_WRITE");
            assertThat(dto.roles()).containsExactly("ADMIN");
        }
    }
}
