package com.example.tgs_dev.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Stable business identity for a {@link Vehicle} across SCD Type-2 versions.
 *
 * <p>{@code natural_key} holds {@code vehicleNumber} at group-creation time.
 * Uniqueness is enforced per-company at the DB level via
 * {@code uq_vehicle_group_company_key}.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "vehicle_group", schema = "core")
public class VehicleGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "natural_key", nullable = false)
    private String naturalKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public VehicleGroup(Company company, String naturalKey) {
        this.company    = company;
        this.naturalKey = naturalKey;
    }
}
