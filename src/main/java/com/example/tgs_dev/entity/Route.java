package com.example.tgs_dev.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name="route", schema = "core")
@SQLRestriction("active = true")
public class Route extends BaseAudit{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="id")
    private Integer id;

    @Column(name="route_number")
    private String routeNumber;

    @Column(name="active")
    private Boolean active = true;

    @Column(name="base_duration")
    private Integer baseDuration;

    @Column(name="cycle_count")
    private int cycleCount;

    public Route(String routeNumber, Integer baseDuration, int cycleCount) {
        this.routeNumber = routeNumber;
        this.baseDuration = baseDuration;
        this.cycleCount = cycleCount;
    }
}
