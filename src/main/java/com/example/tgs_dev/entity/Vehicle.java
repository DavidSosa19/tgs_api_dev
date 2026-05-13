package com.example.tgs_dev.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name="vehicle", schema = "core")
@SQLRestriction("active = true")
public class Vehicle extends BaseAudit{

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

    public Vehicle(String vehicleNumber, Person owner) {
        this.vehicleNumber = vehicleNumber;
        this.owner = owner;
    }

}
