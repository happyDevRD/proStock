package com.happydev.prestockbackend.repository;

import com.happydev.prestockbackend.entity.WhatsAppInboundMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface WhatsAppInboundMessageRepository extends JpaRepository<WhatsAppInboundMessage, Long> {

    boolean existsByWamid(String wamid);

    /** Para no repetir la respuesta automática en cada mensaje de la misma conversación. */
    boolean existsByFromPhoneAndAutoRepliedTrueAndReceivedAtAfter(String fromPhone, Instant after);

    long countBySeenAtIsNull();

    /** No vistos en la bandeja (los archivados no cuentan). */
    long countBySeenAtIsNullAndArchivedAtIsNull();

    List<WhatsAppInboundMessage> findAllByOrderBySentAtDesc(Pageable pageable);

    List<WhatsAppInboundMessage> findByArchivedAtIsNullOrderBySentAtDesc(Pageable pageable);

    List<WhatsAppInboundMessage> findBySavedTrueOrderBySentAtDesc(Pageable pageable);

    List<WhatsAppInboundMessage> findByArchivedAtIsNotNullOrderBySentAtDesc(Pageable pageable);

    @Modifying
    @Query("update WhatsAppInboundMessage m set m.seenAt = :at where m.seenAt is null")
    int markAllSeen(@Param("at") Instant at);
}
