package com.example.tgs_dev.controller;

import com.example.tgs_dev.controller.request.LoginRequest;
import com.example.tgs_dev.controller.request.RefreshRequest;
import com.example.tgs_dev.controller.response.CompanyContextDTO;
import com.example.tgs_dev.controller.response.UserContextDTO;
import com.example.tgs_dev.entity.Company;
import com.example.tgs_dev.entity.User;
import com.example.tgs_dev.mapper.UserContextMapper;
import com.example.tgs_dev.mapper.UserMapper;
import com.example.tgs_dev.security.JwtService;
import com.example.tgs_dev.service.UserService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


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
    @DisplayName("POST /api/auth/login")
    class Login {

        @Test
        @DisplayName("returns 200 with access and refresh tokens on successful authentication")
        void returnsTokensOnSuccess() {
            LoginRequest req = new LoginRequest("jdoe", "secret");
            User user = userWithCompany();

            when(userDetailsService.loadUserByUsername("jdoe")).thenReturn(user);
            when(jwtService.generateAccessToken(user)).thenReturn("access-token");
            when(jwtService.generateRefreshToken(user)).thenReturn("refresh-token");

            var response = sut.login(req);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().data().accessToken()).isEqualTo("access-token");
            assertThat(response.getBody().data().refreshToken()).isEqualTo("refresh-token");
            verify(authManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        }

        @Test
        @DisplayName("calls authManager.authenticate with the supplied credentials")
        void authenticatesCredentials() {
            LoginRequest req = new LoginRequest("jdoe", "secret");
            User user = userWithCompany();

            when(userDetailsService.loadUserByUsername("jdoe")).thenReturn(user);
            when(jwtService.generateAccessToken(any())).thenReturn("a");
            when(jwtService.generateRefreshToken(any())).thenReturn("r");

            sut.login(req);

            verify(authManager).authenticate(argThat(token ->
                    "jdoe".equals(token.getPrincipal()) && "secret".equals(token.getCredentials())));
        }
    }

    @Nested
    @DisplayName("POST /api/auth/refresh")
    class Refresh {

        @Test
        @DisplayName("returns 200 with new token pair when refresh token is valid")
        void returnsNewTokensOnValidRefresh() {
            String refreshToken = "valid-refresh";
            User user = userWithCompany();

            Claims claims = mock(Claims.class);
            when(claims.get("type", String.class)).thenReturn("refresh");
            when(claims.getSubject()).thenReturn("jdoe");
            when(jwtService.validateAndExtract(refreshToken)).thenReturn(claims);
            when(userDetailsService.loadUserByUsername("jdoe")).thenReturn(user);
            when(jwtService.generateAccessToken(user)).thenReturn("new-access");
            when(jwtService.generateRefreshToken(user)).thenReturn("new-refresh");

            var response = sut.refresh(new RefreshRequest(refreshToken));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().accessToken()).isEqualTo("new-access");
        }

        @Test
        @DisplayName("returns 401 when token type is not 'refresh'")
        void returns401ForWrongTokenType() {
            Claims claims = mock(Claims.class);
            when(claims.get("type", String.class)).thenReturn("access");   // wrong type
            when(jwtService.validateAndExtract("bad-type")).thenReturn(claims);

            var response = sut.refresh(new RefreshRequest("bad-type"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("returns 401 when token is invalid (JwtException)")
        void returns401ForInvalidToken() {
            when(jwtService.validateAndExtract("expired")).thenThrow(new JwtException("expired"));

            var response = sut.refresh(new RefreshRequest("expired"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
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
