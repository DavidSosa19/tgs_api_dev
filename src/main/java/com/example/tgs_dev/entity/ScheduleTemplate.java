package com.example.tgs_dev.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name="schedule_template",schema="core")
public class ScheduleTemplate extends BaseAudit implements Activatable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id", nullable = false)
    private Route route;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "secondary_route_id", nullable = true)
    private Route secondaryRoute;

    @Column(name="template_number", nullable = false)
    private String templateNumber;

    @Column(name="template_name", nullable = false)
    private String name;

    @Column(name="active")
    private Boolean active = true;

    /**
     * Position of this template in the vehicle departure order for its route.
     * Vehicles are dispatched in ascending {@code sequenceOrder}.
     *
     * <p>In round-robin slot assignment, the vehicle at position {@code p} (1-based)
     * receives departure slots {@code p}, {@code p + N}, {@code p + 2N}, ... where
     * N is the total number of vehicles active for the route on that day.
     */
    @Min(1)
    @Column(name = "sequence_order", nullable = false)
    private Integer sequenceOrder;

    // ── SCD Type-2 versioning fields (populated by V01 migration) ────────────

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private ScheduleTemplateGroup group;

    @Column(name = "version_from")
    private LocalDateTime versionFrom;

    @Column(name = "version_to")
    private LocalDateTime versionTo;

    @Column(name = "is_current")
    private Boolean isCurrent = true;

    public ScheduleTemplate(Route route, String templateNumber, String name, Integer sequenceOrder) {
        this.route          = route;
        this.templateNumber = templateNumber;
        this.name           = name;
        this.sequenceOrder  = sequenceOrder;
    }
}
