package com.happydev.prestockbackend.whatsapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Llamadas a la Cloud API de Meta (graph.facebook.com). Solo hace HTTP: qué se envía y cuándo
 * lo deciden los servicios. En modo SIMULATE u OFF no sale nada hacia Meta.
 */
@Component
public class WhatsAppClient {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppClient.class);

    private final WhatsAppProperties props;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public WhatsAppClient(WhatsAppProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
    }

    /** Resultado de un envío: {@code wamid} si Meta lo aceptó; si no, el motivo. */
    public record SendResult(boolean accepted, String wamid, String error) {
        static SendResult ok(String wamid) {
            return new SendResult(true, wamid, null);
        }

        static SendResult failed(String error) {
            return new SendResult(false, null, error);
        }
    }

    public boolean isLive() {
        return props.getMode() == WhatsAppProperties.Mode.LIVE
                && !props.getPhoneNumberId().isBlank() && !props.getAccessToken().isBlank();
    }

    /** Texto libre: Meta solo lo entrega si el cliente escribió en las últimas 24 h. */
    public SendResult sendText(String to, String text) {
        ObjectNode body = mapper.createObjectNode();
        body.put("messaging_product", "whatsapp");
        body.put("recipient_type", "individual");
        body.put("to", to);
        body.put("type", "text");
        ObjectNode textNode = body.putObject("text");
        textNode.put("preview_url", false);
        textNode.put("body", text);
        return post(body);
    }

    private SendResult post(ObjectNode body) {
        if (!isLive()) {
            log.info("WhatsApp ({}): no se envía a Meta: {}", props.getMode(), body);
            return SendResult.failed("modo " + props.getMode());
        }
        String url = props.getGraphUrl() + "/" + props.getApiVersion() + "/" + props.getPhoneNumberId() + "/messages";
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + props.getAccessToken())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode json = res.body() == null || res.body().isBlank() ? mapper.createObjectNode() : mapper.readTree(res.body());
            if (res.statusCode() / 100 == 2) {
                String wamid = json.path("messages").path(0).path("id").asText(null);
                return wamid != null ? SendResult.ok(wamid) : SendResult.failed("Meta no devolvió el id del mensaje");
            }
            JsonNode error = json.path("error");
            String detail = error.path("code").asText("?") + ": "
                    + error.path("error_data").path("details").asText(error.path("message").asText("HTTP " + res.statusCode()));
            log.warn("WhatsApp rechazó el envío: {}", detail);
            return SendResult.failed(detail);
        } catch (IOException e) {
            log.warn("No se pudo llamar a WhatsApp: {}", e.getMessage());
            return SendResult.failed("sin conexión con Meta: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return SendResult.failed("interrumpido");
        }
    }
}
