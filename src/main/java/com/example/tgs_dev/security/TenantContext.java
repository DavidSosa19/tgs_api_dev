package com.example.tgs_dev.security;

/**
 * Thread-local holder for the current tenant (company) identifier.
 *
 * <p>Lifecycle:
 * <ol>
 *   <li>{@link #set(Integer)} is called by {@code JwtAuthenticationFilter} right after
 *       the authenticated {@code User} is resolved from the DB.</li>
 *   <li>{@link #get()} / {@link #require()} are called by service-layer methods to scope
 *       every repository query to the current tenant.</li>
 *   <li>{@link #clear()} is called in the {@code finally} block of the same filter to
 *       prevent context leakage between requests on thread-pool threads.</li>
 * </ol>
 */
public final class TenantContext {

    private static final ThreadLocal<Integer> COMPANY_ID = new ThreadLocal<>();

    private TenantContext() {}

    /** Stores the current tenant's company ID in the thread-local. */
    public static void set(Integer companyId) {
        COMPANY_ID.set(companyId);
    }

    /**
     * Returns the current tenant's company ID, or {@code null} if no tenant
     * context has been established (e.g. public/unauthenticated requests).
     */
    public static Integer get() {
        return COMPANY_ID.get();
    }

    /**
     * Returns the current tenant's company ID.
     *
     * @throws IllegalStateException if no tenant context is set.
     */
    public static Integer require() {
        Integer id = COMPANY_ID.get();
        if (id == null) {
            throw new IllegalStateException(
                    "No tenant context available. Ensure the request is authenticated.");
        }
        return id;
    }

    /** Removes the company ID from the thread-local (call in {@code finally}). */
    public static void clear() {
        COMPANY_ID.remove();
    }
}
