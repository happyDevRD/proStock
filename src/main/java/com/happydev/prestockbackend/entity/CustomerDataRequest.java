package com.happydev.prestockbackend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Formulario de datos enviado al cliente por WhatsApp (WhatsApp Flows). La respuesta llega por el
 * webhook con el mismo {@code token} (flow_token) y completa la ficha del cliente.
 */
@Entity
@Table(name = "customer_data_requests")
@Getter
@Setter
@NoArgsConstructor
public class CustomerDataRequest {

    public enum Status { SENT, COMPLETED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_order_id")
    private ServiceOrder serviceOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "whatsapp_message_id")
    private WhatsAppMessage whatsappMessage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.SENT;

    @Column(columnDefinition = "TEXT")
    private String response;

    @Column(name = "result_detail", length = 500)
    private String resultDetail;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;
}
