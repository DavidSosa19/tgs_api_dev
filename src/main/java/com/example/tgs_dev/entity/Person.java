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
@Table(name="person", schema = "core")
@SQLRestriction("active = true")
public class Person extends BaseAudit{

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

    public Person(String documentNumber, String firstName, String secondName, String firstLastName, String secondLastName) {
        this.documentNumber = documentNumber;
        this.firstName = firstName;
        this.secondName = secondName;
        this.firstLastName = firstLastName;
        this.secondLastName = secondLastName;
    }
}
