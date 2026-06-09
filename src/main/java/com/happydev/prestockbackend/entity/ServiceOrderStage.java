package com.happydev.prestockbackend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "service_order_stages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ServiceOrderStage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_order_id", nullable = false)
    private ServiceOrder serviceOrder;

    @Column(name = "stage_name", nullable = false, length = 50)
    private String stageName;

    @Column(name = "entered_at", nullable = false)
    private LocalDateTime enteredAt;

    @Column(length = 500)
    private String notes;

    @Column(length = 100)
    private String actor;
}
