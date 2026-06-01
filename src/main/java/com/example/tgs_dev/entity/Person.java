package com.example.tgs_dev.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import com.example.tgs_dev.entity.Company;
import java.io.Serializable;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name="person", schema = "core")
public class Person extends BaseAudit implements Activatable, Serializable {

    @java.io.Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="id")
    private Integer id;

    @Column(name="document_number")
    private String documentNumber;

    @Column(name="first_name")
    private String firstName;

    @Column(name="second_name")
    private String secondName;

    @Column(name="first_last_name")
    private String firstLastName;

    @Column(name="second_last_name")
    private String secondLastName;

    @Column(name="active")
    private Boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    // ── SCD Type-2 versioning fields (populated by V01 migration) ────────────

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private PersonGroup group;

    /** Instant (inclusive) when this version became current. */
    @Column(name = "version_from")
    private LocalDateTime versionFrom;

    /** Instant (exclusive) when this version was superseded; {@code null} = still current. */
    @Column(name = "version_to")
    private LocalDateTime versionTo;

    /**
     * {@code true} for the single row that represents the live state of this group.
     * {@code false} for all historical versions.
     */
    @Column(name = "is_current")
    private Boolean isCurrent = true;

    public Person(String documentNumber, String firstName, String secondName, String firstLastName, String secondLastName) {
        this.documentNumber = documentNumber;
        this.firstName = firstName;
        this.secondName = secondName;
        this.firstLastName = firstLastName;
        this.secondLastName = secondLastName;
    }
}
