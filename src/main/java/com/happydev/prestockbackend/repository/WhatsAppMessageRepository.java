package com.happydev.prestockbackend.repository;

import com.happydev.prestockbackend.entity.WhatsAppMessage;
import com.happydev.prestockbackend.entity.WhatsAppMessageKind;
import com.happydev.prestockbackend.entity.WhatsAppMessageStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WhatsAppMessageRepository extends JpaRepository<WhatsAppMessage, Long> {

    boolean existsByDedupeKey(String dedupeKey);

    Optional<WhatsAppMessage> findByWamid(String wamid);

    @Query("""
            select m from WhatsAppMessage m
             where m.status = com.happydev.prestockbackend.entity.WhatsAppMessageStatus.PENDING
               and (m.nextAttemptAt is null or m.nextAttemptAt <= :now)
             order by m.id""")
    List<WhatsAppMessage> findDue(@Param("now") Instant now, Pageable pageable);

    /**
     * Al arrancar: lo que quedó a medio enviar vuelve a la cola. Si el corte fue justo después
     * de que Meta lo aceptara, el cliente podría recibirlo dos veces; es raro y preferible a perderlo.
     */
    @Modifying
    @Query("update WhatsAppMessage m set m.status = com.happydev.prestockbackend.entity.WhatsAppMessageStatus.PENDING"
            + " where m.status = com.happydev.prestockbackend.entity.WhatsAppMessageStatus.SENDING")
    int requeueInterrupted();

    List<WhatsAppMessage> findByServiceOrderIdOrderByCreatedAtDesc(Long serviceOrderId);

    List<WhatsAppMessage> findByCustomerIdOrderByCreatedAtDesc(Long customerId, Pageable pageable);

    boolean existsByServiceOrderIdAndKindInAndStatusInAndCreatedAtAfter(
            Long serviceOrderId, Collection<WhatsAppMessageKind> kinds,
            Collection<WhatsAppMessageStatus> statuses, Instant after);
}
