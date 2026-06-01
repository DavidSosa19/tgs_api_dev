package com.example.tgs_dev.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * The identity of a transit corridor.
 *
 * <p>A {@code Route} represents the physical path (e.g. "Ruta 7") and is
 * intentionally free of any operational parameters such as cycle count or
 * departure gaps.  All scheduling behaviour is controlled by the
 * {@link RouteOperationalPeriod} associated with each route for a given
 * date range (school year, vacation, etc.).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "route", schema = "core")
public class Route extends BaseAudit implements Activatable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "route_number")
    private String routeNumber;

    @Column(name = "active")
    private Boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    // ── SCD Type-2 versioning fields (populated by V01 migration) ────────────

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private RouteGroup group;

    @Column(name = "version_from")
    private LocalDateTime versionFrom;

    @Column(name = "version_to")
    private LocalDateTime versionTo;

    @Column(name = "is_current")
    private Boolean isCurrent = true;

    public Route(String routeNumber) {
        this.routeNumber = routeNumber;
    }
}
