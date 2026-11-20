package com.happydev.prestockbackend.calendar;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

/**
 * Llamadas mínimas a Google Calendar API v3 con una cuenta de servicio, sin librerías de Google:
 * el JWT se firma con jjwt (RS256) y se cambia por un token de acceso en {@code token_uri}.
 * El token se guarda en memoria hasta un minuto antes de vencer.
 */
@Component
public class GoogleCalendarClient {

    static final String SCOPE = "https://www.googleapis.com/auth/calendar.events";
    private static final String API = "https://www.googleapis.com/calendar/v3";

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper mapper;

    private String cachedFor;
    private String cachedToken;
    private Instant cachedUntil = Instant.EPOCH;

    public GoogleCalendarClient(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** Datos útiles de la llave JSON de la cuenta de servicio. */
    public record ServiceAccount(String clientEmail, String tokenUri, PrivateKey key) {}

    public ServiceAccount parse(String json) {
        try {
            JsonNode n = mapper.readTree(json);
            String email = n.path("client_email").asText("");
            String pem = n.path("private_key").asText("");
            String tokenUri = n.path("token_uri").asText("https://oauth2.googleapis.com/token");
            if (email.isBlank() || pem.isBlank()) {
                throw new CalendarException("La llave no es de una cuenta de servicio (faltan client_email o private_key).", false);
            }
            String base64 = pem.replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s+", "");
            PrivateKey key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
            return new ServiceAccount(email, tokenUri, key);
        } catch (CalendarException e) {
            throw e;
        } catch (Exception e) {
            throw new CalendarException("No se pudo leer la llave de la cuenta de servicio: " + e.getMessage(), false);
        }
    }

    synchronized String token(String serviceAccountJson) {
        if (serviceAccountJson.equals(cachedFor) && Instant.now().isBefore(cachedUntil)) return cachedToken;
        ServiceAccount sa = parse(serviceAccountJson);
        Instant now = Instant.now();
        String assertion = Jwts.builder()
                .issuer(sa.clientEmail())
                .audience().single(sa.tokenUri())
                .claim("scope", SCOPE)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(3600)))
                .signWith(sa.key(), Jwts.SIG.RS256)
                .compact();
        String form = "grant_type=" + URLEncoder.encode("urn:ietf:params:oauth:grant-type:jwt-bearer", StandardCharsets.UTF_8)
                + "&assertion=" + URLEncoder.encode(assertion, StandardCharsets.UTF_8);
        HttpResponse<String> res = send(HttpRequest.newBuilder(URI.create(sa.tokenUri()))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build());
        if (res.statusCode() != 200) {
            throw new CalendarException("Google rechazó la cuenta de servicio (" + res.statusCode() + "): " + brief(res.body()), false);
        }
        try {
            JsonNode n = mapper.readTree(res.body());
            cachedFor = serviceAccountJson;
            cachedToken = n.path("access_token").asText();
            cachedUntil = now.plusSeconds(Math.max(60, n.path("expires_in").asLong(3600) - 60));
            return cachedToken;
        } catch (IOException e) {
            throw new CalendarException("Respuesta inesperada de Google al pedir el token.", true);
        }
    }

    /** Nombre del calendario (sirve para comprobar que la cuenta de servicio tiene acceso). */
    public String calendarSummary(String saJson, String calendarId) {
        // events.list cabe en el permiso «solo eventos» y su respuesta trae el nombre del calendario.
        HttpResponse<String> res = call(saJson, "GET", "/calendars/" + enc(calendarId) + "/events?maxResults=1", null);
        if (res.statusCode() == 404) {
            throw new CalendarException("No se encontró el calendario o no está compartido con la cuenta de servicio.", false);
        }
        check(res, "consultar el calendario");
        try {
            return mapper.readTree(res.body()).path("summary").asText(calendarId);
        } catch (IOException e) {
            return calendarId;
        }
    }

    /** Crea o actualiza el evento con ese id (los ids de ProStock son fijos por orden o bloqueo). */
    public void upsert(String saJson, String calendarId, String eventId, Map<String, Object> event) {
        String path = "/calendars/" + enc(calendarId) + "/events/" + enc(eventId);
        HttpResponse<String> res = call(saJson, "PUT", path, event);
        if (res.statusCode() == 404) {
            Map<String, Object> withId = new java.util.LinkedHashMap<>(event);
            withId.put("id", eventId);
            res = call(saJson, "POST", "/calendars/" + enc(calendarId) + "/events", withId);
        }
        check(res, "guardar el evento");
    }

    /** Borra el evento; si ya no existe, no es error. */
    public void delete(String saJson, String calendarId, String eventId) {
        HttpResponse<String> res = call(saJson, "DELETE", "/calendars/" + enc(calendarId) + "/events/" + enc(eventId), null);
        if (res.statusCode() == 404 || res.statusCode() == 410) return;
        check(res, "borrar el evento");
    }

    private HttpResponse<String> call(String saJson, String method, String path, Object body) {
        String json;
        try {
            json = body == null ? null : mapper.writeValueAsString(body);
        } catch (IOException e) {
            throw new CalendarException("No se pudo preparar el evento.", false);
        }
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(API + path))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + token(saJson));
        if (json != null) {
            b.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(json));
        } else {
            b.method(method, HttpRequest.BodyPublishers.noBody());
        }
        return send(b.build());
    }

    private HttpResponse<String> send(HttpRequest req) {
        try {
            return http.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            // La red del estudio corta conexiones TLS a veces: se reintenta en la siguiente vuelta.
            throw new CalendarException("Sin conexión con Google: " + e.getMessage(), true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CalendarException("Interrumpido.", true);
        }
    }

    private static void check(HttpResponse<String> res, String what) {
        int s = res.statusCode();
        if (s >= 200 && s < 300) return;
        boolean retry = s == 429 || s >= 500;
        String hint = s == 403 ? " ¿El calendario está compartido con la cuenta de servicio con permiso para hacer cambios?" : "";
        throw new CalendarException("No se pudo " + what + " en Google Calendar (" + s + "): " + brief(res.body()) + hint, retry);
    }

    private static String brief(String body) {
        if (body == null) return "";
        String b = body.replaceAll("\\s+", " ").trim();
        return b.length() > 200 ? b.substring(0, 200) + "…" : b;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** Error al hablar con Google; {@code retryable}: probar de nuevo más tarde tiene sentido. */
    public static class CalendarException extends RuntimeException {
        private final boolean retryable;

        public CalendarException(String message, boolean retryable) {
            super(message);
            this.retryable = retryable;
        }

        public boolean isRetryable() {
            return retryable;
        }
    }
}
