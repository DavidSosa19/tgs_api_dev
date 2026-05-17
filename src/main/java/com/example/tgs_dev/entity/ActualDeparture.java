package com.example.tgs_dev.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name="actual_departure", schema = "core")
public class ActualDeparture extends BaseAudit{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="id")
    private Integer id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_id", nullable = false)
    private Schedule schedule;

    @Column(name="actual_time")
    private LocalTime actualTime;

    @Column(name="delay_seconds")
    private Integer delaySeconds;

    public ActualDeparture(Schedule schedule, LocalTime actualTime, Integer delaySeconds) {
        this.schedule = schedule;
        this.actualTime = actualTime;
        this.delaySeconds = delaySeconds;
    }
}