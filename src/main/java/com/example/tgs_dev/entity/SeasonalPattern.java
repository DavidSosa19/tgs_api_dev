package com.example.tgs_dev.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Named seasonal duration configuration for a route.
 *
 * <p>A seasonal pattern overrides the route's default (and calendar overrides)
 * for all dates in the {@code [seasonFrom, seasonTo]} inclusive range.  It is the
 * highest-priority source in the {@link com.example.tgs_dev.service.schedule.DurationResolver}
 * chain.
 *
 * <h3>Overlap resolution</h3>
 * When two seasonal patterns cover the same date for the same route, the one
 * with the lowest {@code id} (first created) wins.  Enforcement is handled by
 * {@link com.example.tgs_dev.service.SeasonalPatternService}.
 *
 * <h3>Annual repetition</h3>
 * In Phase 4, patterns use absolute {@link LocalDate} values.  For annual
 * repetition (e.g. "every December"), create one record per year or introduce a
 * {@code MonthDay}-based model in a future migration (V11).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "route_seasonal_pattern", schema = "core")
public class SeasonalPattern extends BaseAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id", nullable = false)
    private Route route;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** First day of the pattern period (inclusive). */
    @Column(name = "season_from", nullable = false)
    private LocalDate seasonFrom;

    /** Last day of the pattern period (inclusive). */
    @Column(name = "season_to", nullable = false)
    private LocalDate seasonTo;

    @Column(name = "use_time_ranges", nullable = false)
    private Boolean useTimeRanges = false;

    /** Fixed duration for the entire season, or fallback when ranges have gaps. */
    @Column(name = "base_duration", nullable = false)
    private Integer baseDuration;

    @OneToMany(mappedBy = "pattern", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<SeasonalPatternRange> ranges = new ArrayList<>();

    public SeasonalPattern(Route route, Company company, String name,
                           LocalDate seasonFrom, LocalDate seasonTo,
                           Boolean useTimeRanges, Integer baseDuration) {
        this.route         = route;
        this.company       = company;
        this.name          = name;
        this.seasonFrom    = seasonFrom;
        this.seasonTo      = seasonTo;
        this.useTimeRanges = useTimeRanges;
        this.baseDuration  = baseDuration;
    }

    /** Returns {@code true} when {@code date} falls in {@code [seasonFrom, seasonTo]}. */
    public boolean covers(LocalDate date) {
        return !date.isBefore(seasonFrom) && !date.isAfter(seasonTo);
    }
}
