package com.example.tgs_dev.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

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
@SQLRestriction("active = true")
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

    public Route(String routeNumber) {
        this.routeNumber = routeNumber;
    }
}
