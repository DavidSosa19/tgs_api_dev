package com.example.tgs_dev.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name="vehicle_assignment", schema = "core")
@SQLRestriction("active = true")
public class VehicleAssignment extends BaseAudit implements Activatable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="id")
    private Integer id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_operation_id", nullable = false)
    private RouteOperation routeOperation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private ScheduleTemplate scheduleTemplate;

    @Column(name="row_order", nullable = false)
    private Integer rowOrder;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Column(name = "removed_at")
    private LocalDateTime removedAt;

    @Column(name = "removal_reason")
    private String removalReason;

    @Column(name = "origin", nullable = false)
    private String origin = "ORIGINAL";

    @Column(name = "replaces_id")
    private Long replacesId;

    @Column(name = "replaced_by_id")
    private Long replacedById;

    public VehicleAssignment(RouteOperation routeOperation, Vehicle vehicle, ScheduleTemplate scheduleTemplate, Integer rowOrder) {
        this.routeOperation = routeOperation;
        this.vehicle = vehicle;
        this.scheduleTemplate = scheduleTemplate;
        this.rowOrder = rowOrder;
    }
}
