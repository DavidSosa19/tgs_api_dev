package com.example.tgs_dev.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

/**
 * A single time-window entry within a {@link RouteCalendarOverride}.
 *
 * <p>Structurally identical to {@link RouteTimeRange} but scoped to one specific
 * calendar date instead of the route's default configuration.  Instances are
 * owned by their parent override via cascade-all + orphan-removal.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "route_calendar_override_range", schema = "core")
public class RouteCalendarOverrideRange extends BaseAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "override_id", nullable = false)
    private RouteCalendarOverride override;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "range_start", nullable = false)
    private LocalTime rangeStart;

    @Column(name = "range_end", nullable = false)
    private LocalTime rangeEnd;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "crosses_midnight", nullable = false)
    private boolean crossesMidnight = false;

    public RouteCalendarOverrideRange(LocalTime rangeStart, LocalTime rangeEnd,
                                      int durationMinutes, int sortOrder, boolean crossesMidnight) {
        this.rangeStart      = rangeStart;
        this.rangeEnd        = rangeEnd;
        this.durationMinutes = durationMinutes;
        this.sortOrder       = sortOrder;
        this.crossesMidnight = crossesMidnight;
    }
}
