package com.example.tgs_dev.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name="driver_assignment", schema = "core")
public class DriverAssignment extends BaseAudit{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="id")
    private Integer id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_assignment_id", nullable = false)
    private VehicleAssignment vehicleAssignment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id", nullable = false)
    private Person driver;

    public DriverAssignment(VehicleAssignment vehicleAssignment, Person driver) {
        this.vehicleAssignment = vehicleAssignment;
        this.driver = driver;
    }
}
