package com.example.tgs_dev.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import com.example.tgs_dev.entity.Company;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name="vehicle", schema = "core")
public class Vehicle extends BaseAudit implements Activatable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="id")
    private Integer id;

    @Column(name="vehicle_number", nullable = false, unique = true)
    private String vehicleNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = true)
    private Person owner;

    @Column(name="license_plate", nullable = false)
    private String licensePlate;

    @Column(name="active", nullable = false)
    private Boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    // ── SCD Type-2 versioning fields (populated by V01 migration) ────────────

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private VehicleGroup group;

    @Column(name = "version_from")
    private LocalDateTime versionFrom;

    @Column(name = "version_to")
    private LocalDateTime versionTo;

    @Column(name = "is_current")
    private Boolean isCurrent = true;

    public Vehicle(String vehicleNumber, Person owner) {
        this.vehicleNumber = vehicleNumber;
        this.owner = owner;
    }

}
