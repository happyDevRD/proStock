package com.happydev.prestockbackend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Aviso por WhatsApp (plantilla de Meta). Se guarda antes de enviarse y conserva su
 * resultado: es a la vez la bandeja de salida y el historial que ve el estudio.
 */
@Entity
@Table(name = "whatsapp_messages")
@Getter
@Setter
@NoArgsConstructor
public class WhatsAppMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private WhatsAppMessageKind kind;

    @Column(name = "template_name", nullable = false, length = 100)
    private String templateName;

    @Column(nullable = false, length = 10)
    private String language;

    /** E.164 sin "+" (como lo pide la API); null si el cliente no tiene un número válido. */
    @Column(name = "recipient_phone", length = 20)
    private String recipientPhone;

    @Column(name = "recipient_name", length = 160)
    private String recipientName;

    /** Variables del cuerpo en orden, como arreglo JSON. */
    @Column(name = "body_params", nullable = false, columnDefinition = "TEXT")
    private String bodyParams;

    @Column(name = "button_param", length = 200)
    private String buttonParam;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_order_id")
    private ServiceOrder serviceOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_request_id")
    private BookingRequest bookingRequest;

    @Column(name = "dedupe_key", nullable = false, unique = true, length = 200)
    private String dedupeKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WhatsAppMessageStatus status;

    @Column(name = "status_detail", length = 500)
    private String statusDetail;

    @Column(length = 128)
    private String wamid;

    @Column(nullable = false)
    private int attempts = 0;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "sent_at")
    private Instant sentAt;

    /** Lo informa el webhook de Meta. */
    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
