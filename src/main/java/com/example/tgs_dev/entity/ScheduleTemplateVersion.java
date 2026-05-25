package com.example.tgs_dev.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * A named date-bounded {@code startTime} override for a {@link ScheduleTemplate}.
 *
 * <p>When a {@link com.example.tgs_dev.entity.RouteOperation} is initialised,
 * the scheduling engine looks for an active version whose
 * {@code [effectiveFrom, effectiveTo]} window contains the operation's
 * service date.  If found, {@code startTime} from this version takes
 * precedence over the ScheduleTemplate's own field.  If no version matches,
 * the template's {@code startTime} is used as a fallback.
 *
 * <p>Only one active version may be in effect for a given
 * (template, company, date) combination.  The service layer enforces
 * non-overlap at write time.
 *
 * <p>Versions are tenant-scoped, consistent with the rest of the system.
 *
 * <h3>Usage examples</h3>
 * <ul>
 *   <li>"Horario escolar" — startTime=06:00, Jan 15–Nov 29</li>
 *   <li>"Horario vacacional" — startTime=07:00, Dec 1–Jan 14</li>
 * </ul>
 *
 * <h3>Historical integrity</h3>
 * <p>Because schedule entries ({@link Schedule}) materialise the actual
 * departure times at generation time, past operations are unaffected when a
 * new version is created or the current version's end date is set.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "schedule_template_version", schema = "core")
@SQLRestriction("active = true")
public class ScheduleTemplateVersion extends BaseAudit implements Activatable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private ScheduleTemplate template;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    /** Human-readable label, e.g. "Horario vacacional dic-ene 2024". */
    @Column(name = "label", length = 100)
    private String label;

    /**
     * Effective start time for this version.
     * Overrides {@link ScheduleTemplate#getStartTime()} during schedule generation.
     */
    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    /** First date (inclusive) this version is in effect. */
    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    /**
     * Last date (inclusive) this version is in effect.
     * {@code null} means the version is open-ended (no planned end date).
     */
    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    public ScheduleTemplateVersion(ScheduleTemplate template, Company company, String label,
                                   LocalTime startTime, LocalDate effectiveFrom,
                                   LocalDate effectiveTo) {
        this.template      = template;
        this.company       = company;
        this.label         = label;
        this.startTime     = startTime;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo   = effectiveTo;
    }
}
