package com.example.tgs_dev.repository;

import com.example.tgs_dev.entity.Vehicle;
import com.example.tgs_dev.repository.base.BaseRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link Vehicle} entities.
 *
 * <p>{@code @SQLRestriction("active = true")} has been removed. Active-only
 * filtering is explicit via
 * {@link com.example.tgs_dev.repository.specification.TenantSpecifications#isActive()}.
 *
 * <h3>SCD queries</h3>
 * {@code ...ByGroupId} methods are used for user-facing navigation; the inherited
 * {@code findById} (entity surrogate ID) is reserved for internal FK resolution.
 */
public interface VehicleRepository extends BaseRepository<Vehicle, Integer> {

    // ── SCD navigation queries ────────────────────────────────────────────────

    /**
     * Returns the current <em>active</em> version for the given vehicle group,
     * with its owner pre-loaded to avoid LazyInitializationException (OSIV off).
     */
    @Query("""
            SELECT v FROM Vehicle v
            LEFT JOIN FETCH v.owner
            WHERE v.group.id   = :groupId
            AND   v.company.id = :companyId
            AND   v.isCurrent  = true
            AND   v.active     = true
            """)
    Optional<Vehicle> findCurrentActiveByGroupId(@Param("groupId")   Long    groupId,
                                                 @Param("companyId") Integer companyId);

    /** Returns the current version (active or deactivated) for update/reactivate flows. */
    @Query("""
            SELECT v FROM Vehicle v
            LEFT JOIN FETCH v.owner
            WHERE v.group.id   = :groupId
            AND   v.company.id = :companyId
            AND   v.isCurrent  = true
            """)
    Optional<Vehicle> findCurrentByGroupId(@Param("groupId")   Long    groupId,
                                           @Param("companyId") Integer companyId);

    /**
     * Returns all current versions (active + inactive) for the company listing.
     * Owner is JOIN FETCHed to prevent LazyInitializationException (OSIV off).
     */
    @Query("""
            SELECT v FROM Vehicle v
            LEFT JOIN FETCH v.owner
            WHERE v.company.id = :companyId
            AND   v.isCurrent  = true
            ORDER BY v.vehicleNumber
            """)
    List<Vehicle> findAllCurrentByCompany(@Param("companyId") Integer companyId);

    // ── Legacy active-only queries (used by sub-resources / internal FK) ──────

    /** Returns true if any active vehicle lists the given person as owner. */
    boolean existsByOwnerIdAndActiveTrue(Integer ownerId);

    // ── Transitional: reactivate via UPDATE (Phase 2 → Phase 3 will replace) ──

    /**
     * Finds a vehicle by ID regardless of its {@code active} status, scoped to
     * the current tenant (IDOR prevention).  Used by the Phase-2 reactivate flow.
     */
    @Query("""
            SELECT v FROM Vehicle v
            WHERE v.id = :id
            AND   v.company.id = :companyId
            """)
    Optional<Vehicle> findByIdAnyStatus(@Param("id") Integer id, @Param("companyId") Integer companyId);
}
