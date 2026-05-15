package com.example.tgs_dev.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "route_operation", schema = "core")
@SQLRestriction("active = true")
public class RouteOperation extends BaseAudit implements Activatable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id", nullable = false)
    private Route route;

    @Column(name = "service_date", nullable = false)
    private LocalDate serviceDate;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    public RouteOperation(Route route, LocalDate serviceDate) {
        this.route = route;
        this.serviceDate = serviceDate;
    }
}