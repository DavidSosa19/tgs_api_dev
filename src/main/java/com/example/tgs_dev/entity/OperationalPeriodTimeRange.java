package com.example.tgs_dev.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

/**
 * A single time-window entry that defines the departure gap for a specific
 * time slot within a {@link RouteOperationalPeriod}.
 *
 * <p>Mirrors {@link RouteTimeRange} structurally, but is owned by a
 * {@link RouteOperationalPeriod} instead of a {@link Route}.  This allows
 * each operational period (school year, vacation, special event) to carry
 * its own independent time-range configuration without touching the base
 * route data.
 *
 * <p>Instances are owned by {@link RouteOperationalPeriod} via cascade-all +
 * orphan-removal.  They are never managed independently.
 *
 * <h3>Boundary semantics</h3>
 * Matching uses the half-open interval {@code [rangeStart, rangeEnd)}.
 * See {@link com.example.tgs_dev.service.schedule.RouteTimeRangeResolver} for
 * the full specification, including the overnight ({@code crossesMidnight}) case.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "operational_period_time_range", schema = "core")
public class OperationalPeriodTimeRange extends BaseAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "period_id", nullable = false)
    private RouteOperationalPeriod period;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "range_start", nullable = false)
    private LocalTime rangeStart;

    @Column(name = "range_end", nullable = false)
    private LocalTime rangeEnd;

    /** Trip duration (minutes) when a vehicle departs within this time window. */
    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    /**
     * Target departure-slot spacing (minutes) when the current slot falls within
     * this time window.
     *
     * <p>This is independent of {@code durationMinutes}: duration describes how long
     * the trip takes; headway describes how soon the next vehicle should depart.
     * Used by the {@link com.example.tgs_dev.service.schedule.HeadwayResolver} chain.
     */
    @Column(name = "headway_minutes", nullable = false)
    private int headwayMinutes;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** When {@code true}, the range wraps past midnight ({@code rangeEnd < rangeStart}). */
    @Column(name = "crosses_midnight", nullable = false)
    private boolean crossesMidnight = false;

    public OperationalPeriodTimeRange(LocalTime rangeStart, LocalTime rangeEnd,
                                       int durationMinutes, int headwayMinutes,
                                       int sortOrder, boolean crossesMidnight) {
        this.rangeStart      = rangeStart;
        this.rangeEnd        = rangeEnd;
        this.durationMinutes = durationMinutes;
        this.headwayMinutes  = headwayMinutes;
        this.sortOrder       = sortOrder;
        this.crossesMidnight = crossesMidnight;
    }
}
