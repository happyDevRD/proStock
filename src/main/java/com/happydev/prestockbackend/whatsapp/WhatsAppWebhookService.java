package com.happydev.prestockbackend.whatsapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.happydev.prestockbackend.entity.WhatsAppInboundMessage;
import com.happydev.prestockbackend.entity.WhatsAppMessage;
import com.happydev.prestockbackend.entity.WhatsAppMessageStatus;
import com.happydev.prestockbackend.repository.CustomerRepository;
import com.happydev.prestockbackend.repository.WhatsAppInboundMessageRepository;
import com.happydev.prestockbackend.repository.WhatsAppMessageRepository;
import com.happydev.prestockbackend.service.StudioNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Procesa los avisos del webhook de WhatsApp: guarda lo que escriben los clientes (una sola vez
 * aunque Meta repita el aviso), responde con el texto automático y actualiza el estado de entrega
 * de los avisos que envió ProStock.
 */
@Service
public class WhatsAppWebhookService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookService.class);
    private static final Duration AUTO_REPLY_EVERY = Duration.ofHours(24);
    private static final int PREVIEW_CHARS = 300;

    private final WhatsAppProperties props;
    private final WhatsAppClient client;
    private final WhatsAppInboundMessageRepository inboundRepository;
    private final WhatsAppMessageRepository messageRepository;
    private final CustomerRepository customerRepository;
    private final StudioNotifier notifier;
    private final ObjectMapper mapper;

    public WhatsAppWebhookService(WhatsAppProperties props, WhatsAppClient client,
                                  WhatsAppInboundMessageRepository inboundRepository,
                                  WhatsAppMessageRepository messageRepository,
                                  CustomerRepository customerRepository, StudioNotifier notifier,
                                  ObjectMapper mapper) {
        this.props = props;
        this.client = client;
        this.inboundRepository = inboundRepository;
        this.messageRepository = messageRepository;
        this.customerRepository = customerRepository;
        this.notifier = notifier;
        this.mapper = mapper;
    }

    /** {@code payload} ya viene con la firma comprobada. */
    public void process(byte[] payload) {
        JsonNode root;
        try {
            root = mapper.readTree(payload);
        } catch (java.io.IOException e) {
            log.warn("Webhook de WhatsApp con JSON inválido: {}", e.getMessage());
            return;
        }
        if (!"whatsapp_business_account".equals(root.path("object").asText())) return;
        for (JsonNode entry : root.path("entry")) {
            for (JsonNode change : entry.path("changes")) {
                if (!"messages".equals(change.path("field").asText())) continue;
                JsonNode value = change.path("value");
                String toNumberId = value.path("metadata").path("phone_number_id").asText("");
                if (!props.getPhoneNumberId().isBlank() && !props.getPhoneNumberId().equals(toNumberId)) {
                    log.info("Webhook de WhatsApp para otro número ({}), se ignora", toNumberId);
                    continue;
                }
                Map<String, String> names = new HashMap<>();
                for (JsonNode contact : value.path("contacts")) {
                    names.put(contact.path("wa_id").asText(), contact.path("profile").path("name").asText(null));
                }
                for (JsonNode message : value.path("messages")) {
                    receive(message, names);
                }
                for (JsonNode status : value.path("statuses")) {
                    updateStatus(status);
                }
            }
        }
    }

    private void receive(JsonNode node, Map<String, String> names) {
        String wamid = node.path("id").asText(null);
        String from = node.path("from").asText(null);
        if (wamid == null || from == null || inboundRepository.existsByWamid(wamid)) return;

        String type = node.path("type").asText("unknown");
        WhatsAppInboundMessage message = new WhatsAppInboundMessage();
        message.setWamid(wamid);
        message.setFromPhone(from);
        String name = names.get(from);
        message.setProfileName(name == null || name.isBlank() ? null : name.length() > 160 ? name.substring(0, 160) : name);
        message.setType(type.length() > 30 ? type.substring(0, 30) : type);
        message.setSentAt(epoch(node.path("timestamp").asText(null)));
        message.setContextWamid(node.path("context").path("id").asText(null));
        fillContent(message, node, type);
        message.setRaw(node.toString());
        if (from.length() >= 10) {
            customerRepository.findFirstByPhoneDigits(from.substring(from.length() - 10)).ifPresent(message::setCustomer);
        }
        try {
            message = inboundRepository.save(message);
        } catch (DataIntegrityViolationException e) {
            // Meta repitió el aviso y otro hilo lo guardó primero.
            return;
        }

        notifyStudio(message);
        autoReply(message);
    }

    private void fillContent(WhatsAppInboundMessage message, JsonNode node, String type) {
        switch (type) {
            case "text" -> message.setBody(node.path("text").path("body").asText(null));
            case "image", "video", "audio", "document", "sticker" -> {
                JsonNode media = node.path(type);
                message.setMediaId(media.path("id").asText(null));
                message.setMediaMime(media.path("mime_type").asText(null));
                message.setMediaFilename(media.path("filename").asText(null));
                message.setBody(media.path("caption").asText(null));
            }
            case "button" -> message.setBody(node.path("button").path("text").asText(null));
            case "interactive" -> {
                JsonNode interactive = node.path("interactive");
                JsonNode reply = interactive.has("button_reply") ? interactive.path("button_reply") : interactive.path("list_reply");
                message.setBody(reply.path("title").asText(null));
            }
            case "reaction" -> message.setBody(node.path("reaction").path("emoji").asText(null));
            case "location" -> {
                JsonNode location = node.path("location");
                String place = (location.path("name").asText("") + " " + location.path("address").asText("")).trim();
                message.setBody(location.path("latitude").asText() + "," + location.path("longitude").asText()
                        + (place.isEmpty() ? "" : " " + place));
            }
            default -> {
                // contacts, order, unsupported…: queda en raw
            }
        }
    }

    private void notifyStudio(WhatsAppInboundMessage message) {
        String who = message.getCustomer() != null
                ? (message.getCustomer().getFirstName() + " " + message.getCustomer().getLastName()).trim()
                : message.getProfileName() != null ? message.getProfileName() : "Sin nombre";
        String content = message.getBody() != null ? message.getBody() : "[" + message.getType() + "]";
        if (content.length() > PREVIEW_CHARS) content = content.substring(0, PREVIEW_CHARS) + "…";
        notifier.send("WhatsApp de " + who + " (+" + message.getFromPhone() + "): " + content);
    }

    private void autoReply(WhatsAppInboundMessage message) {
        String text = props.getAutoReply();
        if (text == null || text.isBlank() || "reaction".equals(message.getType())) return;
        Instant since = Instant.now().minus(AUTO_REPLY_EVERY);
        if (inboundRepository.existsByFromPhoneAndAutoRepliedTrueAndReceivedAtAfter(message.getFromPhone(), since)) return;
        WhatsAppClient.SendResult result = client.sendText(message.getFromPhone(), text);
        if (result.accepted()) {
            message.setAutoReplied(true);
            inboundRepository.save(message);
        }
    }

    private void updateStatus(JsonNode node) {
        String wamid = node.path("id").asText(null);
        if (wamid == null) return;
        messageRepository.findByWamid(wamid).ifPresent(message -> {
            Instant at = epoch(node.path("timestamp").asText(null));
            switch (node.path("status").asText("")) {
                case "delivered" -> {
                    if (message.getDeliveredAt() == null) message.setDeliveredAt(at);
                }
                case "read" -> {
                    if (message.getDeliveredAt() == null) message.setDeliveredAt(at);
                    if (message.getReadAt() == null) message.setReadAt(at);
                }
                case "failed" -> {
                    JsonNode error = node.path("errors").path(0);
                    String detail = error.path("code").asText("?") + ": "
                            + error.path("error_data").path("details").asText(error.path("title").asText("sin detalle"));
                    message.setStatus(WhatsAppMessageStatus.FAILED);
                    message.setStatusDetail(detail.length() > 500 ? detail.substring(0, 500) : detail);
                }
                default -> {
                    return;
                }
            }
            messageRepository.save(message);
        });
    }

    private static Instant epoch(String seconds) {
        try {
            return seconds == null ? Instant.now() : Instant.ofEpochSecond(Long.parseLong(seconds));
        } catch (NumberFormatException e) {
            return Instant.now();
        }
    }
}
