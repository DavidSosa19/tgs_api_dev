package com.example.tgs_dev.repository;

import com.example.tgs_dev.entity.Person;
import com.example.tgs_dev.repository.base.BaseRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link Person} entities.
 *
 * <p>{@code @SQLRestriction("active = true")} has been removed. All active-only
 * filtering is now explicit via
 * {@link com.example.tgs_dev.repository.specification.TenantSpecifications#isActive()}.
 *
 * <h3>SCD queries</h3>
 * After the V01 migration every person row carries a {@code group_id} linking it
 * to a {@link com.example.tgs_dev.entity.PersonGroup}.  Use the {@code ...ByGroupId}
 * methods for user-facing navigation; use the inherited {@code findById} (entity
 * surrogate ID) only for internal FK resolution.
 */
public interface PersonRepository extends BaseRepository<Person, Integer> {

    /**
     * Returns the current <em>active</em> version for the given group, scoped to
     * the current tenant.  Used for user-facing navigation ({@code GET /persons/{groupId}}).
     *
     * @param groupId   the {@link com.example.tgs_dev.entity.PersonGroup} id
     * @param companyId the current tenant's company ID (tenant scope)
     */
    @Query("""
            SELECT p FROM Person p
            WHERE p.group.id   = :groupId
            AND   p.company.id = :companyId
            AND   p.isCurrent  = true
            AND   p.active     = true
            """)
    Optional<Person> findCurrentActiveByGroupId(@Param("groupId")   Long    groupId,
                                                @Param("companyId") Integer companyId);

    /**
     * Returns the current version (active or deactivated) for the given group.
     * Used when the service needs to close the current version before creating a
     * new one (update or reactivate flows).
     */
    @Query("""
            SELECT p FROM Person p
            WHERE p.group.id   = :groupId
            AND   p.company.id = :companyId
            AND   p.isCurrent  = true
            """)
    Optional<Person> findCurrentByGroupId(@Param("groupId")   Long    groupId,
                                          @Param("companyId") Integer companyId);

    /**
     * Returns all current versions (active + inactive) for the company listing.
     * The frontend uses this to show deactivated persons and offer reactivation.
     */
    @Query("""
            SELECT p FROM Person p
            WHERE p.company.id = :companyId
            AND   p.isCurrent  = true
            ORDER BY p.firstLastName, p.firstName
            """)
    List<Person> findAllCurrentByCompany(@Param("companyId") Integer companyId);
}
