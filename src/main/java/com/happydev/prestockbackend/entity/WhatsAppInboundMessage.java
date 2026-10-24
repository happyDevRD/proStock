package com.happydev.prestockbackend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Mensaje que un cliente escribió al número de la API; llega por el webhook de Meta. */
@Entity
@Table(name = "whatsapp_inbound_messages")
@Getter
@Setter
@NoArgsConstructor
public class WhatsAppInboundMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 128)
    private String wamid;

    /** E.164 sin "+", tal como lo manda Meta. */
    @Column(name = "from_phone", nullable = false, length = 20)
    private String fromPhone;

    @Column(name = "profile_name", length = 160)
    private String profileName;

    @Column(nullable = false, length = 30)
    private String type;

    /** Texto del mensaje, o el pie de foto / la etiqueta del botón según el tipo. */
    @Column(columnDefinition = "TEXT")
    private String body;

    @Column(name = "media_id", length = 128)
    private String mediaId;

    @Column(name = "media_mime", length = 100)
    private String mediaMime;

    @Column(name = "media_filename", length = 255)
    private String mediaFilename;

    @Column(name = "context_wamid", length = 128)
    private String contextWamid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt = Instant.now();

    @Column(name = "auto_replied", nullable = false)
    private boolean autoReplied = false;

    @Column(name = "seen_at")
    private Instant seenAt;

    /** El mensaje tal como llegó, por si hace falta algo que no se extrajo. */
    @Column(columnDefinition = "TEXT")
    private String raw;
}
