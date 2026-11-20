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

    /** Usuario con que el cliente entra al portal (p. ej. "nathalie13"). */
    @Column(length = 60)
    private String username;

    /** BCrypt de la contraseña de 6 dígitos; la contraseña en claro solo se muestra al generarla. */
    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    /** Cómo entró la última vez: ENLACE (botón del WhatsApp) o CONTRASENA. */
    @Column(name = "last_login_method", length = 20)
    private String lastLoginMethod;

    /** Dispositivo de la última entrada, resumido del User-Agent ("iPhone · Safari"). */
    @Column(name = "last_login_device", length = 80)
    private String lastLoginDevice;

    /** NONE · PENDING (esperan autorización del estudio) · APPROVED · REJECTED */
    @Column(name = "extra_status", nullable = false, length = 20)
    private String extraStatus = "NONE";

    /** JSON: previas adicionales (las que pasan del cupo), en el orden en que el cliente las marcó. */
    @Column(name = "extra_files", nullable = false, columnDefinition = "TEXT")
    private String extraFiles = "[]";

    /** Línea de la orden con el cobro de las adicionales. */
    @Column(name = "extra_item_id")
    private Long extraItemId;

    @Column(name = "extra_decided_at")
    private LocalDateTime extraDecidedAt;

    @Column(name = "extra_decided_by", length = 100)
    private String extraDecidedBy;

    @Column(name = "extra_notes", length = 500)
    private String extraNotes;

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
