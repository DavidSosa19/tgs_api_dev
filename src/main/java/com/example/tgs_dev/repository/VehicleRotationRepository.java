package com.example.tgs_dev.repository;

import com.example.tgs_dev.entity.VehicleRotation;
import com.example.tgs_dev.entity.enums.ShiftDayType;
import com.example.tgs_dev.repository.base.BaseRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface VehicleRotationRepository extends BaseRepository<VehicleRotation,Integer> {

    @Query("""
            SELECT DISTINCT r FROM VehicleRotation r
            LEFT JOIN FETCH r.entries e
            LEFT JOIN FETCH e.vehicle v
            LEFT JOIN FETCH v.owner
            LEFT JOIN FETCH e.scheduleTemplate st
            LEFT JOIN FETCH st.route
            LEFT JOIN FETCH st.secondaryRoute
            WHERE r.endDate      >= :date
              AND r.startDate    <= :date
              AND r.rotationType  = :rotationType
              AND r.company.id    = :companyId
            """)
    Optional<VehicleRotation> findByDateAndTypeEager(
            @Param("date") LocalDate date,
            @Param("rotationType") ShiftDayType rotationType,
            @Param("companyId") Integer companyId);
}
