package com.example.tgs_dev.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name="schedule", schema = "core")
public class Schedule extends BaseAudit{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="id")
    private Integer id;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_assignment_id", nullable = false)
    private VehicleAssignment vehicleAssignment;

    @Column(name="departure_order", nullable = false)
    private Integer departureOrder;

    @Column(name="departure_time", nullable = false)
    private LocalTime departureTime;

    public Schedule(VehicleAssignment vehicleAssignment, Integer departureOrder, LocalTime departureTime) {
        this.vehicleAssignment = vehicleAssignment;
        this.departureOrder = departureOrder;
        this.departureTime = departureTime;
    }
}
