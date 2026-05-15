package com.example.tgs_dev.security;

import java.util.Arrays;

/**
 * Single source of truth for application roles.
 *
 * Adding a role: declare a new constant here.
 * Changing permissions: update SecurityConfig.filterChain — one place only.
 *
 * Spring Security expects role names WITHOUT the "ROLE_" prefix in
 * hasRole() / hasAnyRole(); the prefix is added automatically by the framework.
 */
public enum AppRole {

    ADMIN,
    USER;

    /** Role name as Spring Security's hasRole/hasAnyRole expect it. */
    public String value() {
        return name();
    }

    /** Utility to get string values for multiple roles at once (for hasAnyRole). */
    public static String[] valuesOf(AppRole... roles) {
        return Arrays.stream(roles)
                .map(AppRole::value)
                .toArray(String[]::new);
    }
}
