package com.happydev.prestockbackend.controller;

import com.happydev.prestockbackend.entity.WhatsAppInboundMessage;
import com.happydev.prestockbackend.entity.WhatsAppMessage;
import com.happydev.prestockbackend.repository.WhatsAppInboundMessageRepository;
import com.happydev.prestockbackend.repository.WhatsAppMessageRepository;
import com.happydev.prestockbackend.whatsapp.CustomerDataRequestService;
import com.happydev.prestockbackend.whatsapp.WhatsAppTriggers;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.Instant;
import java.util.List;

/** Avisos por WhatsApp para las pantallas del estudio: historial por orden, recordatorio de saldo y bandeja. */
@RestController
@PreAuthorize("hasAnyAuthority('ROLE_GESTOR', 'ROLE_ADMIN', 'view.service_orders')")
public class WhatsAppController {

    public record MessageView(Long id, String kind, String kindLabel, String templateName, String recipientPhone,
                              String recipientName, String status, String statusDetail, Instant createdAt,
                              Instant sentAt, Instant deliveredAt, Instant readAt, String createdBy) {
        static MessageView of(WhatsAppMessage m) {
            return new MessageView(m.getId(), m.getKind().name(), m.getKind().label(), m.getTemplateName(),
                    m.getRecipientPhone(), m.getRecipientName(), m.getStatus().name(), m.getStatusDetail(),
                    m.getCreatedAt(), m.getSentAt(), m.getDeliveredAt(), m.getReadAt(), m.getCreatedBy());
        }
    }

    public record InboundView(Long id, String fromPhone, String profileName, Long customerId, String customerName,
                              String type, String body, boolean hasMedia, Instant sentAt, boolean autoReplied,
                              Instant seenAt, boolean saved, Instant archivedAt) {
        static InboundView of(WhatsAppInboundMessage m) {
            var c = m.getCustomer();
            return new InboundView(m.getId(), m.getFromPhone(), m.getProfileName(), c == null ? null : c.getId(),
                    c == null ? null : (c.getFirstName() + " " + c.getLastName()).replace(" -", "").trim(),
                    m.getType(), m.getBody(), m.getMediaId() != null, m.getSentAt(), m.isAutoReplied(), m.getSeenAt(),
                    m.isSaved(), m.getArchivedAt());
        }
    }

    public record InboxView(long unseen, List<InboundView> messages) {}

    private final WhatsAppMessageRepository messageRepository;
    private final WhatsAppInboundMessageRepository inboundRepository;
    private final WhatsAppTriggers triggers;
    private final CustomerDataRequestService dataRequests;

    public WhatsAppController(WhatsAppMessageRepository messageRepository,
                              WhatsAppInboundMessageRepository inboundRepository, WhatsAppTriggers triggers,
                              CustomerDataRequestService dataRequests) {
        this.messageRepository = messageRepository;
        this.inboundRepository = inboundRepository;
        this.triggers = triggers;
        this.dataRequests = dataRequests;
    }

    /** Formularios de datos enviados al cliente de la orden (lo más nuevo primero). */
    @GetMapping("/api/service-orders/{id}/whatsapp/data-requests")
    public List<CustomerDataRequestService.RequestView> dataRequests(@PathVariable Long id) {
        return dataRequests.forOrder(id);
    }

    /** Envía al cliente el formulario para completar sus datos dentro de WhatsApp. */
    @PostMapping("/api/service-orders/{id}/whatsapp/data-requests")
    public CustomerDataRequestService.RequestView requestData(@PathVariable Long id, Principal principal) {
        return dataRequests.request(id, principal == null ? null : principal.getName());
    }

    @GetMapping("/api/service-orders/{id}/whatsapp")
    @Transactional(readOnly = true)
    public List<MessageView> orderMessages(@PathVariable Long id) {
        return messageRepository.findByServiceOrderIdOrderByCreatedAtDesc(id).stream().map(MessageView::of).toList();
    }

    @PostMapping("/api/service-orders/{id}/whatsapp/balance-reminder")
    public MessageView balanceReminder(@PathVariable Long id, Principal principal) {
        return MessageView.of(triggers.balanceReminder(id, principal == null ? null : principal.getName()));
    }

    @GetMapping("/api/customers/{id}/whatsapp")
    @Transactional(readOnly = true)
    public List<MessageView> customerMessages(@PathVariable Long id) {
        return messageRepository.findByCustomerIdOrderByCreatedAtDesc(id, PageRequest.of(0, 50)).stream()
                .map(MessageView::of).toList();
    }

    /**
     * Lo que los clientes escribieron al número de avisos, lo más nuevo primero.
     * {@code view}: inbox (sin archivar, por defecto), saved (guardados) o archived.
     */
    @GetMapping("/api/whatsapp/inbox")
    @Transactional(readOnly = true)
    public InboxView inbox(@RequestParam(defaultValue = "inbox") String view) {
        PageRequest page = PageRequest.of(0, 100);
        List<WhatsAppInboundMessage> list = switch (view) {
            case "saved" -> inboundRepository.findBySavedTrueOrderBySentAtDesc(page);
            case "archived" -> inboundRepository.findByArchivedAtIsNotNullOrderBySentAtDesc(page);
            default -> inboundRepository.findByArchivedAtIsNullOrderBySentAtDesc(page);
        };
        return new InboxView(inboundRepository.countBySeenAtIsNullAndArchivedAtIsNull(),
                list.stream().map(InboundView::of).toList());
    }

    public record InboxUpdate(Boolean saved, Boolean archived) {}

    /** Guardar / quitar de guardados y archivar / devolver a la bandeja. */
    @PatchMapping("/api/whatsapp/inbox/{id}")
    @Transactional
    public InboundView updateInbound(@PathVariable Long id, @RequestBody InboxUpdate body) {
        WhatsAppInboundMessage m = inboundRepository.findById(id)
                .orElseThrow(() -> new com.happydev.prestockbackend.exception.ResourceNotFoundException("Mensaje", "id", id));
        if (body.saved() != null) m.setSaved(body.saved());
        if (body.archived() != null) {
            m.setArchivedAt(body.archived() ? Instant.now() : null);
            if (body.archived() && m.getSeenAt() == null) m.setSeenAt(Instant.now());
        }
        return InboundView.of(inboundRepository.save(m));
    }

    /** Borra el mensaje de ProStock (en el WhatsApp del cliente sigue existiendo). */
    @DeleteMapping("/api/whatsapp/inbox/{id}")
    @Transactional
    public ResponseEntity<Void> deleteInbound(@PathVariable Long id) {
        if (!inboundRepository.existsById(id)) {
            throw new com.happydev.prestockbackend.exception.ResourceNotFoundException("Mensaje", "id", id);
        }
        inboundRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/whatsapp/inbox/seen")
    @Transactional
    public ResponseEntity<Void> markAllSeen() {
        inboundRepository.markAllSeen(Instant.now());
        return ResponseEntity.noContent().build();
    }
}
