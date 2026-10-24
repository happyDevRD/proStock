package com.happydev.prestockbackend.whatsapp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.happydev.prestockbackend.entity.*;
import com.happydev.prestockbackend.repository.WhatsAppMessageRepository;
import com.happydev.prestockbackend.service.FeatureConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Bandeja de salida de avisos por WhatsApp. Cada aviso se guarda primero (con una clave que
 * impide repetirlo) y un despachador lo envía después, con reintentos si Meta o la red fallan.
 * Así un corte de internet no hace fallar la operación del estudio ni se pierde el aviso.
 */
@Service
public class WhatsAppOutbox {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppOutbox.class);
    static final String MODULE = "module.whatsapp";
    static final int MAX_ATTEMPTS = 5;
    private static final int BATCH = 20;

    /** Lo que hay que avisar; {@code consent} = el cliente aceptó recibir avisos por WhatsApp. */
    public record Notice(WhatsAppMessageKind kind, @Nullable String phone, @Nullable String name,
                         List<String> params, @Nullable String buttonParam, boolean consent,
                         @Nullable Customer customer, @Nullable ServiceOrder order,
                         @Nullable BookingRequest booking, String dedupeKey, @Nullable String actor) {
    }

    private final WhatsAppMessageRepository repository;
    private final WhatsAppClient client;
    private final WhatsAppProperties props;
    private final FeatureConfigService features;
    private final ObjectMapper mapper;
    private final Clock clock;

    @Autowired
    public WhatsAppOutbox(WhatsAppMessageRepository repository, WhatsAppClient client, WhatsAppProperties props,
                          FeatureConfigService features, ObjectMapper mapper) {
        this(repository, client, props, features, mapper, Clock.systemUTC());
    }

    WhatsAppOutbox(WhatsAppMessageRepository repository, WhatsAppClient client, WhatsAppProperties props,
                   FeatureConfigService features, ObjectMapper mapper, Clock clock) {
        this.repository = repository;
        this.client = client;
        this.props = props;
        this.features = features;
        this.mapper = mapper;
        this.clock = clock;
    }

    /** ¿Este tipo de aviso está encendido en el Centro de Módulos (y WhatsApp no está apagado)? */
    public boolean isActive(WhatsAppMessageKind kind) {
        if (props.getMode() == WhatsAppProperties.Mode.OFF || !features.isEnabled(MODULE)) return false;
        return kind.feature() == null || features.isEnabled(kind.feature());
    }

    /**
     * Registra el aviso para enviarlo. Devuelve el registro, o null si el tipo está apagado o
     * ese mismo hecho ya se avisó. Sin consentimiento o sin teléfono válido queda como omitido.
     */
    @Transactional
    @Nullable
    public WhatsAppMessage enqueue(Notice notice) {
        if (!isActive(notice.kind()) || repository.existsByDedupeKey(notice.dedupeKey())) return null;

        WhatsAppMessage message = new WhatsAppMessage();
        message.setKind(notice.kind());
        message.setTemplateName(notice.kind().templateName());
        message.setLanguage(props.getLanguage());
        message.setRecipientPhone(WhatsAppText.phone(notice.phone()));
        String name = notice.name() == null ? "" : WhatsAppText.param(notice.name(), "");
        message.setRecipientName(name.isEmpty() ? null : name.substring(0, Math.min(160, name.length())));
        message.setBodyParams(toJson(notice.params()));
        message.setButtonParam(notice.buttonParam());
        message.setCustomer(notice.customer());
        message.setServiceOrder(notice.order());
        message.setBookingRequest(notice.booking());
        message.setDedupeKey(notice.dedupeKey());
        message.setCreatedBy(notice.actor());
        message.setCreatedAt(Instant.now(clock));
        if (!notice.consent()) {
            message.setStatus(WhatsAppMessageStatus.SKIPPED);
            message.setStatusDetail("El cliente no ha aceptado avisos por WhatsApp");
        } else if (message.getRecipientPhone() == null) {
            message.setStatus(WhatsAppMessageStatus.SKIPPED);
            message.setStatusDetail("Sin un teléfono válido");
        } else {
            message.setStatus(WhatsAppMessageStatus.PENDING);
        }
        try {
            return repository.saveAndFlush(message);
        } catch (DataIntegrityViolationException e) {
            return null; // otro hilo registró el mismo hecho
        }
    }

    /** Lo que quedó a medio enviar cuando se apagó el servidor vuelve a la cola. */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void requeueInterrupted() {
        int count = repository.requeueInterrupted();
        if (count > 0) log.info("WhatsApp: {} aviso(s) interrumpidos vuelven a la cola", count);
    }

    @Scheduled(fixedDelayString = "${app.whatsapp.dispatch-every-ms:20000}", initialDelay = 30000)
    public void dispatch() {
        if (props.getMode() == WhatsAppProperties.Mode.OFF) return;
        List<WhatsAppMessage> due = repository.findDue(Instant.now(clock), PageRequest.of(0, BATCH));
        for (WhatsAppMessage message : due) {
            send(message);
        }
    }

    void send(WhatsAppMessage message) {
        message.setStatus(WhatsAppMessageStatus.SENDING);
        message.setAttempts(message.getAttempts() + 1);
        repository.save(message);

        Instant now = Instant.now(clock);
        if (props.getMode() == WhatsAppProperties.Mode.SIMULATE) {
            message.setStatus(WhatsAppMessageStatus.SIMULATED);
            message.setStatusDetail("Modo simulación: no se envió a Meta");
            message.setSentAt(now);
            log.info("WhatsApp (simulado) {} a {}: {}", message.getTemplateName(), message.getRecipientPhone(),
                    message.getBodyParams());
            repository.save(message);
            return;
        }

        WhatsAppClient.SendResult result = client.sendTemplate(message.getRecipientPhone(), message.getTemplateName(),
                message.getLanguage(), fromJson(message.getBodyParams()), message.getButtonParam());
        if (result.accepted()) {
            message.setStatus(WhatsAppMessageStatus.SENT);
            message.setWamid(result.wamid());
            message.setSentAt(now);
            message.setStatusDetail(null);
        } else if (result.retryable() && message.getAttempts() < MAX_ATTEMPTS) {
            message.setStatus(WhatsAppMessageStatus.PENDING);
            message.setNextAttemptAt(now.plus(backoff(message.getAttempts())));
            message.setStatusDetail(truncate("Reintento " + message.getAttempts() + ": " + result.error()));
        } else {
            message.setStatus(WhatsAppMessageStatus.FAILED);
            message.setStatusDetail(truncate(result.error()));
        }
        repository.save(message);
    }

    /** 2, 4, 8, 16 minutos. */
    static Duration backoff(int attempts) {
        return Duration.ofMinutes(1L << Math.min(attempts, 4));
    }

    private static String truncate(@Nullable String text) {
        if (text == null) return null;
        return text.length() > 500 ? text.substring(0, 500) : text;
    }

    private String toJson(List<String> params) {
        try {
            return mapper.writeValueAsString(params);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private List<String> fromJson(String json) {
        try {
            return mapper.readValue(json, new TypeReference<List<String>>() { });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
