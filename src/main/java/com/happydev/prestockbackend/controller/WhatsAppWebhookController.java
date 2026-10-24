package com.happydev.prestockbackend.controller;

import com.happydev.prestockbackend.whatsapp.WhatsAppProperties;
import com.happydev.prestockbackend.whatsapp.WhatsAppSignature;
import com.happydev.prestockbackend.whatsapp.WhatsAppWebhookService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Webhook de WhatsApp Cloud API (público, sin sesión). Llega por el nginx de iridiscencia.com
 * ({@code /api/whatsapp/webhook} → {@code /api/public/whatsapp/webhook}). La seguridad es la
 * firma de Meta: sin {@code WHATSAPP_APP_SECRET} no se acepta ningún aviso.
 */
@RestController
@RequestMapping("/api/public/whatsapp/webhook")
public class WhatsAppWebhookController {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookController.class);

    private final WhatsAppProperties props;
    private final WhatsAppWebhookService service;

    public WhatsAppWebhookController(WhatsAppProperties props, WhatsAppWebhookService service) {
        this.props = props;
        this.service = service;
    }

    /** Meta comprueba la URL al guardarla: hay que devolver el challenge si el token coincide. */
    @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> verify(@RequestParam(name = "hub.mode", required = false) String mode,
                                         @RequestParam(name = "hub.verify_token", required = false) String token,
                                         @RequestParam(name = "hub.challenge", required = false) String challenge) {
        String expected = props.getVerifyToken();
        if ("subscribe".equals(mode) && expected != null && !expected.isBlank() && expected.equals(token)
                && challenge != null) {
            return ResponseEntity.ok(challenge);
        }
        log.warn("Verificación del webhook de WhatsApp rechazada (mode={})", mode);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @PostMapping
    public ResponseEntity<Void> receive(@RequestBody byte[] body,
                                        @RequestHeader(name = "X-Hub-Signature-256", required = false) String signature) {
        if (!WhatsAppSignature.isValid(body, signature, props.getAppSecret())) {
            log.warn("Aviso del webhook de WhatsApp con firma inválida o sin secreto configurado");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        // Si algo falla aquí (p. ej. la base de datos), el 500 hace que Meta lo reintente.
        service.process(body);
        return ResponseEntity.ok().build();
    }
}
