package com.example.tgs_dev.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica que AuditorAware resuelve el auditor correcto en todos los
 * escenarios posibles del SecurityContext.
 *
 * No necesita Spring context: instancia AuditingConfig directamente.
 */
@DisplayName("AuditingConfig")
class AuditingConfigTest {

    private final AuditorAware<String> auditorAware = new AuditingConfig().auditorAware();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ── sin autenticación ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("sin autenticación en el contexto")
    class NoAuthentication {

        @Test
        @DisplayName("contexto vacío → 'system'")
        void emptyContext_returnsSystem() {
            // SecurityContextHolder vacío por defecto
            assertThat(auditorAware.getCurrentAuditor()).contains("system");
        }
    }

    // ── usuario autenticado ───────────────────────────────────────────────────

    @Nested
    @DisplayName("usuario autenticado")
    class Authenticated {

        @Test
        @DisplayName("retorna el username del principal autenticado")
        void authenticated_returnsUsername() {
            var auth = new UsernamePasswordAuthenticationToken(
                    "jdoe", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
            SecurityContextHolder.getContext().setAuthentication(auth);

            assertThat(auditorAware.getCurrentAuditor()).contains("jdoe");
        }

        @Test
        @DisplayName("username distinto de jdoe también se retorna correctamente")
        void differentUser_returnsCorrectUsername() {
            var auth = new UsernamePasswordAuthenticationToken(
                    "admin.user", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
            SecurityContextHolder.getContext().setAuthentication(auth);

            assertThat(auditorAware.getCurrentAuditor()).contains("admin.user");
        }
    }

    // ── usuario anónimo ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("usuario anónimo")
    class Anonymous {

        @Test
        @DisplayName("'anonymousUser' de Spring → 'system'")
        void springAnonymousUser_returnsSystem() {
            var auth = new UsernamePasswordAuthenticationToken(
                    "anonymousUser", null, List.of());
            SecurityContextHolder.getContext().setAuthentication(auth);

            assertThat(auditorAware.getCurrentAuditor()).contains("system");
        }

        @Test
        @DisplayName("AnonymousAuthenticationToken de Spring → 'system'")
        void anonymousAuthToken_returnsSystem() {
            var auth = new AnonymousAuthenticationToken(
                    "key", "anonymousUser",
                    List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
            SecurityContextHolder.getContext().setAuthentication(auth);

            assertThat(auditorAware.getCurrentAuditor()).contains("system");
        }
    }

    // ── resultado siempre presente ────────────────────────────────────────────

    @Nested
    @DisplayName("invariante: nunca retorna Optional vacío")
    class NeverEmpty {

        @Test
        @DisplayName("contexto vacío → Optional no vacío")
        void emptyContext_optionalIsPresent() {
            assertThat(auditorAware.getCurrentAuditor()).isPresent();
        }

        @Test
        @DisplayName("usuario autenticado → Optional no vacío")
        void authenticated_optionalIsPresent() {
            var auth = new UsernamePasswordAuthenticationToken(
                    "someone", null, List.of());
            SecurityContextHolder.getContext().setAuthentication(auth);

            assertThat(auditorAware.getCurrentAuditor()).isPresent();
        }
    }
}
