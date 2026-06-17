package com.happydev.prestockbackend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Llama a la API REST de Gemini (gemini-2.0-flash-lite).
 * La clave se lee de IntegrationCredentialService (proveedor "gemini", clave "api_key").
 */
@Service
public class GeminiService {

    private static final Logger log = LoggerFactory.getLogger(GeminiService.class);
    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash-lite:generateContent";

    private final IntegrationCredentialService credentialService;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public GeminiService(IntegrationCredentialService credentialService, ObjectMapper objectMapper) {
        this.credentialService = credentialService;
        this.restClient = RestClient.create();
        this.objectMapper = objectMapper;
    }

    /**
     * Envía un prompt al modelo y devuelve el texto de respuesta.
     * Retorna null si la clave no está configurada o la llamada falla.
     */
    public String generate(String systemPrompt, String userMessage) {
        String apiKey = credentialService.get("gemini", "api_key").orElse(null);
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Gemini API key no configurada. Configúrala en Ajustes > Integraciones > gemini/api_key");
            return null;
        }

        try {
            ObjectNode body = objectMapper.createObjectNode();
            ArrayNode contents = body.putArray("contents");

            if (systemPrompt != null && !systemPrompt.isBlank()) {
                ObjectNode sys = contents.addObject();
                sys.put("role", "user");
                sys.putArray("parts").addObject().put("text", systemPrompt);
                ObjectNode sysReply = contents.addObject();
                sysReply.put("role", "model");
                sysReply.putArray("parts").addObject().put("text", "Entendido. Voy a ayudarte.");
            }

            ObjectNode userTurn = contents.addObject();
            userTurn.put("role", "user");
            userTurn.putArray("parts").addObject().put("text", userMessage);

            String response = restClient.post()
                    .uri(GEMINI_URL + "?key=" + apiKey)
                    .header("Content-Type", "application/json")
                    .body(objectMapper.writeValueAsString(body))
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            return root.path("candidates").path(0)
                    .path("content").path("parts").path(0)
                    .path("text").asText(null);

        } catch (Exception e) {
            log.error("Error llamando a Gemini API: {}", e.getMessage());
            return null;
        }
    }
}
