package com.happydev.prestockbackend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Avisos al estudio (p. ej. "el cliente confirmó su selección"). Si hay bot de Telegram
 * configurado (TELEGRAM_BOT_TOKEN y TELEGRAM_CHAT_ID, los mismos del monitor), envía el
 * mensaje; si no, solo queda en el log. Nunca bloquea ni hace fallar la operación.
 */
@Service
public class StudioNotifier {

    private static final Logger log = LoggerFactory.getLogger(StudioNotifier.class);

    private final String token;
    private final String chatId;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public StudioNotifier(@Value("${app.notify.telegram.token:}") String token,
                          @Value("${app.notify.telegram.chat-id:}") String chatId) {
        this.token = token.trim();
        this.chatId = chatId.trim();
    }

    public boolean isConfigured() {
        return !token.isEmpty() && !chatId.isEmpty();
    }

    public void send(String message) {
        log.info("Aviso al estudio: {}", message);
        if (!isConfigured()) return;
        String body = "chat_id=" + URLEncoder.encode(chatId, StandardCharsets.UTF_8)
                + "&text=" + URLEncoder.encode(message, StandardCharsets.UTF_8);
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.telegram.org/bot" + token + "/sendMessage"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        http.sendAsync(req, HttpResponse.BodyHandlers.discarding())
                .thenAccept(r -> {
                    if (r.statusCode() >= 300) log.warn("Telegram respondió {}", r.statusCode());
                })
                .exceptionally(e -> {
                    log.warn("No se pudo enviar el aviso por Telegram: {}", e.getMessage());
                    return null;
                });
    }
}
