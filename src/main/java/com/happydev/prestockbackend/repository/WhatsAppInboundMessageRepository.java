package com.happydev.prestockbackend.repository;

import com.happydev.prestockbackend.entity.WhatsAppInboundMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface WhatsAppInboundMessageRepository extends JpaRepository<WhatsAppInboundMessage, Long> {

    boolean existsByWamid(String wamid);

    /** Para no repetir la respuesta automática en cada mensaje de la misma conversación. */
    boolean existsByFromPhoneAndAutoRepliedTrueAndReceivedAtAfter(String fromPhone, Instant after);
}
