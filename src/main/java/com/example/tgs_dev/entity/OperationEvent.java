package com.example.tgs_dev.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor   // required by @Builder when @NoArgsConstructor is present
@Builder
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

}
