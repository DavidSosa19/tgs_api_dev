package com.example.tgs_dev.repository.specification;

import org.springframework.data.jpa.domain.Specification;

/**
 * Reusable JPA {@link Specification} fragments for multi-tenant data isolation
 * and active-record filtering.
 *
 * <p>Every entity that carries a {@code company} association uses
 * {@link #belongsToCompany(Integer)} to restrict query results to the current
 * tenant's records only.
 *
 * <p>Entities that previously used {@code @SQLRestriction("active = true")} now
 * use {@link #isActive()} explicitly so that the filter intent is visible at
 * every call site and can be omitted when inactive records are intentionally
 * included (e.g. the reactivation UI).
 */
public final class TenantSpecifications {

    private TenantSpecifications() {}

    /**
     * Matches entities whose {@code company.id} equals the given {@code companyId}.
     *
     * <p>Assumes the entity has a {@code company} field mapped to a
     * {@link com.example.tgs_dev.entity.Company}.
     *
     * @param companyId the tenant's company primary key; must not be {@code null}.
     */
    public static <T> Specification<T> belongsToCompany(Integer companyId) {
        return (root, query, cb) ->
                cb.equal(root.get("company").get("id"), companyId);
    }

    /**
     * Matches entities whose {@code active} field is {@code true}.
     *
     * <p>Used on entities that previously relied on {@code @SQLRestriction("active = true")}
     * (Person, Vehicle, Route, ScheduleTemplate).  Compose with
     * {@link #belongsToCompany} for the typical tenant + active query:
     * <pre>{@code
     * TenantSpecifications.belongsToCompany(companyId)
     *         .and(TenantSpecifications.isActive())
     * }</pre>
     * Omit this specification when inactive records must be visible
     * (e.g. reactivation listing).
     */
    public static <T> Specification<T> isActive() {
        return (root, query, cb) -> cb.isTrue(root.get("active"));
    }
}
