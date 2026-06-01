package com.example.tgs_dev.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.io.Serializable;

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

    @ManyToOne(fetch = FetchType.EAGER)
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

    @Column(name="start_time")
    private LocalTime startTime;

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

    public ScheduleTemplate(Route route, String templateNumber, String name, LocalTime startTime) {
        this.route = route;
        this.templateNumber = templateNumber;
        this.name = name;
        this.startTime = startTime;
    }
}
