package com.happydev.prestockbackend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Enlace privado para que el cliente elija sus fotos de una orden (fase 3). */
@Entity
@Table(name = "service_order_selections")
@Getter
@Setter
@NoArgsConstructor
public class ServiceOrderSelection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_order_id", nullable = false, unique = true)
    private ServiceOrder serviceOrder;

    @Column(nullable = false, unique = true, length = 64)
    private String token;

    @Column(nullable = false)
    private int quota;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "extra_product_id")
    private Product extraProduct;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /** JSON: lista de nombres de archivo de "2 Seleccion". */
    @Column(name = "selected_files", nullable = false, columnDefinition = "TEXT")
    private String selectedFiles = "[]";

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "extra_count", nullable = false)
    private int extraCount;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
