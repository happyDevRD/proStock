package com.happydev.prestockbackend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Solicitud de reserva enviada desde el formulario de la web (sin sesión). */
@Entity
@Table(name = "booking_requests")
@Getter
@Setter
@NoArgsConstructor
public class BookingRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 40)
    private String phone;

    @Column(length = 160)
    private String email;

    @Column(name = "event_type", length = 120)
    private String eventType;

    @Column(name = "event_date")
    private LocalDate eventDate;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingRequestStatus status = BookingRequestStatus.NEW;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_order_id")
    private ServiceOrder serviceOrder;

    @Column(name = "client_ip", length = 64)
    private String clientIp;

    @Column(name = "handled_by", length = 100)
    private String handledBy;

    @Column(name = "handled_at")
    private LocalDateTime handledAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /** Referencia que ve el cliente en su mensaje de WhatsApp (S-0012). */
    public String getCode() {
        return id == null ? null : codeFor(id);
    }

    public static String codeFor(long id) {
        return "S-" + String.format("%04d", id);
    }
}
