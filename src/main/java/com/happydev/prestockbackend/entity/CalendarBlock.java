package com.happydev.prestockbackend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Fechas u horas en que no se agendan sesiones: para todo el estudio o para una persona. */
@Entity
@Table(name = "calendar_blocks")
@Getter
@Setter
@NoArgsConstructor
public class CalendarBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    /** Exclusivo, igual que el fin de las citas. */
    @Column(name = "ends_at", nullable = false)
    private LocalDateTime endsAt;

    @Column(name = "all_day", nullable = false)
    private boolean allDay = false;

    /** null = todo el estudio. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @Column(nullable = false, length = 200)
    private String reason;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
