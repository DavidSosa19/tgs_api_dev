package com.example.tgs_dev.repository;

import com.example.tgs_dev.entity.User;
import com.example.tgs_dev.repository.base.BaseRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends BaseRepository<User, Integer> {

    /**
     * Eagerly fetches roles, role permissions, company and person so the
     * returned {@link User} can be safely passed to mappers and the JWT filter
     * outside of any session/transaction. {@link User#roles} and
     * {@link User#company} are LAZY by default; without this graph,
     * {@code getAuthorities()} / {@code getCompany()} would throw
     * {@code LazyInitializationException}.
     */
    @EntityGraph(attributePaths = {"roles", "roles.permissions", "company", "person"})
    Optional<User> findByUserName(String userName);

    // ── Admin cross-tenant queries (bypass @SQLRestriction) ──────────────────

    /**
     * Returns ALL users (active and inactive) for admin listing.
     * Bypasses the {@code @SQLRestriction("active = true")} on {@link User}.
     */
    @Query(nativeQuery = true,
            value = "SELECT * FROM core.users ORDER BY created_at DESC")
    List<User> findAllAdmin();

    /**
     * Returns ALL users (active and inactive) belonging to a given company.
     * Bypasses the {@code @SQLRestriction("active = true")} on {@link User}.
     */
    @Query(nativeQuery = true,
            value = "SELECT * FROM core.users WHERE company_id = :companyId ORDER BY created_at DESC")
    List<User> findAllByCompanyIdAdmin(@Param("companyId") Integer companyId);

    /**
     * Finds a user by ID regardless of active status.
     * Bypasses the {@code @SQLRestriction("active = true")} on {@link User}.
     */
    @Query(nativeQuery = true,
            value = "SELECT * FROM core.users WHERE id = :id")
    Optional<User> findByIdAdmin(@Param("id") Long id);

    /**
     * Reactivates a previously deactivated user (sets active = true).
     * Bypasses the {@code @SQLRestriction("active = true")} on {@link User}.
     */
    @Modifying
    @Query(nativeQuery = true,
            value = "UPDATE core.users SET active = true WHERE id = :id")
    void reactivateById(@Param("id") Long id);

    // ── Active-only queries (respect @SQLRestriction) ────────────────────────

    /** Returns all active users belonging to a given company (no TenantSpec needed). */
    List<User> findAllByCompany_Id(Integer companyId);

    /** Checks whether a Person is already linked to a user account (1:1 enforcement). */
    boolean existsByPerson_Id(Integer personId);
}
