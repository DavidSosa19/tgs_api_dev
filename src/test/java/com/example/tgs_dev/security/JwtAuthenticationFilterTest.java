package com.example.tgs_dev.security;

import com.example.tgs_dev.entity.Company;
import com.example.tgs_dev.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for JwtAuthenticationFilter.
 *
 * Uses Spring's MockHttpServlet* and MockFilterChain to exercise
 * the filter without a running server or application context.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthenticationFilter")
class JwtAuthenticationFilterTest {

    @Mock JwtService         jwtService;
    @Mock UserDetailsService userDetailsService;

    @InjectMocks JwtAuthenticationFilter sut;

    MockHttpServletRequest  request;
    MockHttpServletResponse response;
    MockFilterChain         chain;

    @BeforeEach
    void setUp() {
        request  = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        chain    = new MockFilterChain();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    // ── No / non-Bearer header ─────────────────────────────────────────────────

    @Nested @DisplayName("No or non-Bearer authorization header")
    class NoHeader {

        @Test @DisplayName("passes through when Authorization header is absent")
        void noHeader_passesThrough() throws Exception {
            sut.doFilterInternal(request, response, chain);

            assertThat(chain.getRequest()).isNotNull();  // chain was called
            assertThat(response.getStatus()).isEqualTo(200);
            verifyNoInteractions(jwtService, userDetailsService);
        }

        @Test @DisplayName("passes through when Authorization header is not Bearer")
        void basicAuth_passesThrough() throws Exception {
            request.addHeader(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz");

            sut.doFilterInternal(request, response, chain);

            assertThat(chain.getRequest()).isNotNull();
            verifyNoInteractions(jwtService, userDetailsService);
        }
    }

    // ── Valid access token ─────────────────────────────────────────────────────

    @Nested @DisplayName("Valid access token")
    class ValidToken {

        @Test @DisplayName("sets SecurityContext authentication for a valid access token")
        void validToken_setsAuthentication() throws Exception {
            Company company = new Company("Corp", "900-1");
            company.setId(5);
            User user = new User("jdoe", "pw", Set.of(), null, company);
            user.setActive(true);

            Claims claims = mock(Claims.class);
            when(claims.get("type", String.class)).thenReturn("access");
            when(claims.getSubject()).thenReturn("jdoe");
            when(jwtService.validateAndExtract("valid-token")).thenReturn(claims);
            when(userDetailsService.loadUserByUsername("jdoe")).thenReturn(user);

            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer valid-token");
            sut.doFilterInternal(request, response, chain);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
            assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
                    .isEqualTo("jdoe");
        }

        @Test @DisplayName("populates TenantContext with user's company id")
        void validToken_setsTenantContext() throws Exception {
            Company company = new Company("Corp", "900-1");
            company.setId(5);
            User user = new User("jdoe", "pw", Set.of(), null, company);
            user.setActive(true);

            Claims claims = mock(Claims.class);
            when(claims.get("type", String.class)).thenReturn("access");
            when(claims.getSubject()).thenReturn("jdoe");
            when(jwtService.validateAndExtract("valid-token")).thenReturn(claims);
            when(userDetailsService.loadUserByUsername("jdoe")).thenReturn(user);

            // Capture tenant context INSIDE the filter chain
            final Integer[] captured = {null};
            MockFilterChain capturingChain = new MockFilterChain() {
                @Override
                public void doFilter(jakarta.servlet.ServletRequest req,
                                     jakarta.servlet.ServletResponse res)
                        throws java.io.IOException, jakarta.servlet.ServletException {
                    captured[0] = TenantContext.get();
                    super.doFilter(req, res);
                }
            };

            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer valid-token");
            sut.doFilterInternal(request, response, capturingChain);

            assertThat(captured[0]).isEqualTo(5);
        }

        @Test @DisplayName("TenantContext is cleared after filter chain completes")
        void tenantContext_clearedAfterChain() throws Exception {
            Company company = new Company("Corp", "900-1");
            company.setId(5);
            User user = new User("jdoe", "pw", Set.of(), null, company);
            user.setActive(true);

            Claims claims = mock(Claims.class);
            when(claims.get("type", String.class)).thenReturn("access");
            when(claims.getSubject()).thenReturn("jdoe");
            when(jwtService.validateAndExtract("valid-token")).thenReturn(claims);
            when(userDetailsService.loadUserByUsername("jdoe")).thenReturn(user);

            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer valid-token");
            sut.doFilterInternal(request, response, chain);

            assertThat(TenantContext.get()).isNull();
        }
    }

    // ── Wrong token type ───────────────────────────────────────────────────────

    @Nested @DisplayName("Wrong token type")
    class WrongType {

        @Test @DisplayName("refresh token → 401 and chain is NOT called")
        void refreshToken_returns401() throws Exception {
            Claims claims = mock(Claims.class);
            when(claims.get("type", String.class)).thenReturn("refresh");
            when(jwtService.validateAndExtract("refresh-token")).thenReturn(claims);

            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer refresh-token");
            sut.doFilterInternal(request, response, chain);

            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(chain.getRequest()).isNull();  // chain was NOT called
        }
    }

    // ── JWT exceptions ─────────────────────────────────────────────────────────

    @Nested @DisplayName("JWT exceptions")
    class JwtExceptions {

        @Test @DisplayName("expired token → 401 Unauthorized")
        void expiredToken_returns401() throws Exception {
            when(jwtService.validateAndExtract(any()))
                    .thenThrow(mock(ExpiredJwtException.class));

            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer expired-token");
            sut.doFilterInternal(request, response, chain);

            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(chain.getRequest()).isNull();
        }

        @Test @DisplayName("invalid token → 401 Unauthorized")
        void invalidToken_returns401() throws Exception {
            when(jwtService.validateAndExtract(any()))
                    .thenThrow(mock(JwtException.class));

            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer bad-token");
            sut.doFilterInternal(request, response, chain);

            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(chain.getRequest()).isNull();
        }

        @Test @DisplayName("401 response body contains success:false and JSON content type")
        void errorBody_isJson() throws Exception {
            when(jwtService.validateAndExtract(any()))
                    .thenThrow(mock(JwtException.class));

            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer bad-token");
            sut.doFilterInternal(request, response, chain);

            assertThat(response.getContentType()).contains("application/json");
            assertThat(response.getContentAsString()).contains("\"success\": false");
        }
    }
}
