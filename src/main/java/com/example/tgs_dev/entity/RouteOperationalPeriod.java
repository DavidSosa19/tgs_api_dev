package com.example.tgs_dev.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * A named date-bounded configuration for a {@link Route}.
 *
 * <p>When a {@link com.example.tgs_dev.entity.RouteOperation} is initialised,
 * the scheduling engine looks for an active period whose
 * {@code [effectiveFrom, effectiveTo]} window contains the operation's
 * service date.  If found, {@code baseDuration} and {@code cycleCount} from
 * this period take precedence over the Route-level defaults.  If no period
 * matches, the Route's own fields are used as a fallback — ensuring backward
 * compatibility with existing data.
 *
 * <p>Only one active period may be in effect for a given (route, company, date)
 * combination.  The service layer enforces non-overlap at write time.
 *
 * <p>Periods are tenant-scoped: the same physical corridor can be operated
 * with different parameters by different companies.
 *
 * <h3>Usage examples</h3>
 * <ul>
 *   <li>"Año escolar 2024" — cycleCount=12, baseDuration=30, Jan 15–Nov 29</li>
 *   <li>"Vacaciones dic-ene" — cycleCount=9, baseDuration=40, Dec 1–Jan 14</li>
 * </ul>
 *
 * <h3>Relationship to {@code Route}</h3>
 * <p>{@code Route.baseDuration} and {@code Route.cycleCount} remain as
 * last-resort defaults and are not removed to preserve backward compatibility.
 * New operational configuration should always be managed through periods.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "route_operational_period", schema = "core")
@SQLRestriction("active = true")
public class RouteOperationalPeriod extends BaseAudit implements Activatable {

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

    /** Human-readable label for the period, e.g. "Año escolar 2024". */
    @Column(name = "label", nullable = false, length = 100)
    private String label;

    /** Base duration (minutes) used for schedule generation during this period. */
    @Column(name = "base_duration", nullable = false)
    private int baseDuration;

    /** Number of departure cycles used for schedule generation during this period. */
    @Column(name = "cycle_count", nullable = false)
    private int cycleCount;

    /** First date (inclusive) this period is in effect. */
    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    /**
     * Last date (inclusive) this period is in effect.
     * {@code null} means the period is open-ended (no planned end date).
     */
    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    /**
     * When {@code true}, departure gaps are resolved from {@link #timeRanges}
     * rather than using {@link #baseDuration} as a flat value.
     * Mirrors the same flag on {@link Route} but applies only within this period.
     */
    @Column(name = "use_time_ranges", nullable = false)
    private Boolean useTimeRanges = false;

    /**
     * Time-range entries that define dynamic departure gaps for this period.
     *
     * <p>Only evaluated when {@link #useTimeRanges} is {@code true}.
     * Managed via cascade ALL + orphan-removal: replacing this list atomically
     * replaces all ranges for the period.  Always ordered by {@code sortOrder}
     * (assigned by the service layer before each save).
     */
    @OneToMany(mappedBy = "period", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<OperationalPeriodTimeRange> timeRanges = new ArrayList<>();

    public RouteOperationalPeriod(Route route, Company company, String label,
                                  int baseDuration, int cycleCount,
                                  LocalDate effectiveFrom, LocalDate effectiveTo) {
        this.route         = route;
        this.company       = company;
        this.label         = label;
        this.baseDuration  = baseDuration;
        this.cycleCount    = cycleCount;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo   = effectiveTo;
    }

    /** Convenience getter — avoids null-check on the column default. */
    public boolean isUseTimeRanges() {
        return Boolean.TRUE.equals(useTimeRanges);
    }
}
