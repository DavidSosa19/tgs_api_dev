package com.example.tgs_dev.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name="vehicle_maintenance", schema = "core")
public class VehicleMaintenance extends BaseAudit{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @Column(name="start_time")
    private LocalDateTime startTime;

    @Column(name="end_time")
    private LocalDateTime endTime;

    @Column(name="maintenance_type")
    private String maintenanceType;

    @Column(name="notes")
    private String notes;

    public VehicleMaintenance(Vehicle vehicle, LocalDateTime startTime, LocalDateTime endTime, String maintenanceType, String notes) {
        this.vehicle = vehicle;
        this.startTime = startTime;
        this.endTime = endTime;
        this.maintenanceType = maintenanceType;
        this.notes = notes;
    }
}
