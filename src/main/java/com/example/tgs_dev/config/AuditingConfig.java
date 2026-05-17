package com.example.tgs_dev.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Proporciona el auditor actual para los campos createdBy / updatedBy de BaseAudit.
 *
 * Casos cubiertos:
 *  - Request autenticada con JWT  → username del token
 *  - Sin autenticación en contexto → "system" (seeds, migraciones)
 *  - Usuario anónimo              → "system"
 */
@Configuration
public class AuditingConfig {

    private static final String SYSTEM_USER    = "system";
    private static final String ANONYMOUS_USER = "anonymousUser";

    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();

            if (auth == null || !auth.isAuthenticated()) {
                return Optional.of(SYSTEM_USER);
            }

            String name = auth.getName();
            return Optional.of(ANONYMOUS_USER.equals(name) ? SYSTEM_USER : name);
        };
    }
}
