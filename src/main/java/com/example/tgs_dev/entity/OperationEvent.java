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
@Table(name="operation_event", schema = "core")
public class OperationEvent extends BaseAudit{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_operation_id", nullable = false)
    private RouteOperation routeOperation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id", nullable = false)
    private Person driver;

    @Column(name="event_type")
    private String eventType;

    @Column(name="event_time")
    private LocalDateTime eventTime;

    @Column(name="start_time")
    private LocalDateTime startTime;

    @Column(name="end_time")
    private LocalDateTime endTime;

    @Column(name="planned_time")
    private Integer plannedTime;

    @Column(name="delay_seconds")
    private Integer delaySeconds;

    public OperationEvent(RouteOperation routeOperation, Vehicle vehicle, Person driver, String eventType, LocalDateTime eventTime, LocalDateTime startTime, LocalDateTime endTime, Integer plannedTime, Integer delaySeconds) {
        this.routeOperation = routeOperation;
        this.vehicle = vehicle;
        this.driver = driver;
        this.eventType = eventType;
        this.eventTime = eventTime;
        this.startTime = startTime;
        this.endTime = endTime;
        this.plannedTime = plannedTime;
        this.delaySeconds = delaySeconds;
    }
}
