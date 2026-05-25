package com.example.tgs_dev.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

/**
 * A single time-window entry within a {@link SeasonalPattern}.
 *
 * <p>Structurally identical to {@link RouteTimeRange} and
 * {@link RouteCalendarOverrideRange}, but scoped to a seasonal date range instead
 * of a specific date or the route default.  Instances are owned by their parent
 * pattern via cascade-all + orphan-removal.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "route_seasonal_pattern_range", schema = "core")
public class SeasonalPatternRange extends BaseAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pattern_id", nullable = false)
    private SeasonalPattern pattern;

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

    public SeasonalPatternRange(LocalTime rangeStart, LocalTime rangeEnd,
                                int durationMinutes, int sortOrder, boolean crossesMidnight) {
        this.rangeStart      = rangeStart;
        this.rangeEnd        = rangeEnd;
        this.durationMinutes = durationMinutes;
        this.sortOrder       = sortOrder;
        this.crossesMidnight = crossesMidnight;
    }
}
