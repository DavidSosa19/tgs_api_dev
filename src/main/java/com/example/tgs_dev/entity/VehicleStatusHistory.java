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
@Table(name="vehicle_status_history", schema = "core")
public class VehicleStatusHistory extends BaseAudit{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="id")
    private Integer id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @Column(name="status")
    private String status;

    @Column(name="start_time")
    private LocalDateTime startTime;

    @Column(name="end_time")
    private LocalDateTime endTime;

    @Column(name="reason")
    private String reason;

    public VehicleStatusHistory(Vehicle vehicle, String status, LocalDateTime startTime, LocalDateTime endTime, String reason) {
        this.vehicle = vehicle;
        this.status = status;
        this.startTime = startTime;
        this.endTime = endTime;
        this.reason = reason;
    }
}
