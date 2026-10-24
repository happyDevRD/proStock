package com.happydev.prestockbackend.whatsapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

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

    /**
     * Resultado de un envío: {@code wamid} si Meta lo aceptó; si no, el motivo y si vale la pena
     * reintentar (sin conexión, error de Meta o límite de velocidad).
     */
    public record SendResult(boolean accepted, String wamid, String error, boolean retryable) {
        public SendResult(boolean accepted, String wamid, String error) {
            this(accepted, wamid, error, false);
        }

        static SendResult ok(String wamid) {
            return new SendResult(true, wamid, null, false);
        }

        static SendResult failed(String error) {
            return new SendResult(false, null, error, false);
        }

        static SendResult retry(String error) {
            return new SendResult(false, null, error, true);
        }
    }

    /** Códigos de Meta que son pasajeros: error interno, servicio no disponible, límites de velocidad. */
    private static final java.util.Set<Integer> RETRYABLE_CODES = java.util.Set.of(1, 2, 4, 80007, 130429, 131000, 131016, 131048, 131056, 133004);

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

    /**
     * Plantilla aprobada. {@code bodyParams} llenan {{1}}, {{2}}… del cuerpo en orden;
     * {@code buttonParam}, si no es null, completa la URL dinámica del primer botón.
     */
    public SendResult sendTemplate(String to, String template, String language, List<String> bodyParams,
                                   @Nullable String buttonParam) {
        ObjectNode body = mapper.createObjectNode();
        body.put("messaging_product", "whatsapp");
        body.put("recipient_type", "individual");
        body.put("to", to);
        body.put("type", "template");
        ObjectNode tpl = body.putObject("template");
        tpl.put("name", template);
        tpl.putObject("language").put("code", language);
        ArrayNode components = tpl.putArray("components");
        if (!bodyParams.isEmpty()) {
            ArrayNode params = components.addObject().put("type", "body").putArray("parameters");
            for (String value : bodyParams) {
                params.addObject().put("type", "text").put("text", value);
            }
        }
        if (buttonParam != null) {
            ObjectNode button = components.addObject();
            button.put("type", "button");
            button.put("sub_type", "url");
            button.put("index", "0");
            button.putArray("parameters").addObject().put("type", "text").put("text", buttonParam);
        }
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
            boolean retryable = res.statusCode() >= 500 || RETRYABLE_CODES.contains(error.path("code").asInt(-1));
            return retryable ? SendResult.retry(detail) : SendResult.failed(detail);
        } catch (IOException e) {
            log.warn("No se pudo llamar a WhatsApp: {}", e.getMessage());
            return SendResult.retry("sin conexión con Meta: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return SendResult.failed("interrumpido");
        }
    }
}
