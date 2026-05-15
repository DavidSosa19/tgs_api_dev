package com.example.tgs_dev.entity;

import com.example.tgs_dev.entity.enums.ShiftDayType;
import com.example.tgs_dev.util.JSONConverter;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name="vehicle_rotation", schema = "core")
@SQLRestriction("active = true")
public class VehicleRotation extends BaseAudit implements Activatable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="id")
    private Integer id;

    @Column(name="start_date")
    private LocalDate startDate;

    @Column(name="end_date")
    private LocalDate endDate;

    @Column(name="active", nullable = false)
    private Boolean active = true;

    @Enumerated(EnumType.STRING)
    @Column(name="rotation_type", nullable = false)
    private ShiftDayType rotationType;

    @OneToMany(mappedBy = "vehicleRotation", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RotationEntry> entries;

    public VehicleRotation(LocalDate startDate, LocalDate endDate, Boolean active, ShiftDayType rotationType) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.active = active;
        this.rotationType = rotationType;
    }
}
