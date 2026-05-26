package com.example.tgs_dev.repository;

import com.example.tgs_dev.entity.RotationEntry;
import com.example.tgs_dev.entity.VehicleRotation;
import com.example.tgs_dev.repository.base.BaseRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RotationEntryRepository extends BaseRepository<RotationEntry, Integer> {

    /**
     * Loads all entries for a rotation together with every association the
     * mapper needs, in a single JOIN-FETCH query.
     * <p>
     * Using LEFT JOIN FETCH on associations covered by {@code @SQLRestriction}
     * (Vehicle, ScheduleTemplate) is intentional: if an associated entity is
     * soft-deleted after being assigned to a rotation the JOIN simply returns
     * null for that association (mappers already handle null inputs) instead
     * of throwing a LazyInitializationException or ObjectNotFoundException.
     */
    @Query("""
            SELECT e FROM RotationEntry e
            LEFT JOIN FETCH e.vehicle v
            LEFT JOIN FETCH v.owner
            LEFT JOIN FETCH e.scheduleTemplate st
            LEFT JOIN FETCH st.route
            LEFT JOIN FETCH st.secondaryRoute
            WHERE e.vehicleRotation = :rotation
            ORDER BY e.listPosition ASC
            """)
    List<RotationEntry> findByRotationEager(@Param("rotation") VehicleRotation rotation);
}
