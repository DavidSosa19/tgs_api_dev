package com.example.tgs_dev.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Date-specific duration override for a route.
 *
 * <p>When a {@code RouteCalendarOverride} exists for a (route, date) pair, the
 * {@link com.example.tgs_dev.service.schedule.CalendarOverrideDurationResolver}
 * uses it instead of the route's own time-range or fixed configuration.
 *
 * <h3>Semantics</h3>
 * <ul>
 *   <li>{@code useTimeRanges = false}: the entire day uses {@code baseDuration}.</li>
 *   <li>{@code useTimeRanges = true}: departure times are matched against {@code ranges};
 *       gaps fall back to {@code baseDuration} (not the route's default).</li>
 * </ul>
 *
 * <h3>Uniqueness</h3>
 * Enforced at DB level: one override per (route_id, override_date, company_id).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "route_calendar_override", schema = "core",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_route_calendar_override",
                columnNames = {"route_id", "override_date", "company_id"}))
public class RouteCalendarOverride extends BaseAudit {

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

    @Column(name = "override_date", nullable = false)
    private LocalDate overrideDate;

    @Column(name = "use_time_ranges", nullable = false)
    private Boolean useTimeRanges = false;

    /** Fixed duration for this day, or fallback when {@code useTimeRanges=true} and no range matches. */
    @Column(name = "base_duration", nullable = false)
    private Integer baseDuration;

    @OneToMany(mappedBy = "override", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<RouteCalendarOverrideRange> ranges = new ArrayList<>();

    public RouteCalendarOverride(Route route, Company company, LocalDate overrideDate,
                                 Boolean useTimeRanges, Integer baseDuration) {
        this.route         = route;
        this.company       = company;
        this.overrideDate  = overrideDate;
        this.useTimeRanges = useTimeRanges;
        this.baseDuration  = baseDuration;
    }
}
