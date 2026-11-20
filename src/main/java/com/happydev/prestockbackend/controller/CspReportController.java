package com.happydev.prestockbackend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recibe los informes de la Content-Security-Policy (hoy en modo Report-Only) y los deja en el log
 * como «CSP: …», para saber con datos reales si hacerla obligatoria rompería algo.
 * Acepta el formato antiguo ({@code report-uri}, {"csp-report":{…}}) y el nuevo (Reporting API, lista).
 */
@RestController
@RequestMapping("/api/public/csp-report")
public class CspReportController {

    private static final Logger log = LoggerFactory.getLogger(CspReportController.class);
    private static final int MAX_BODY = 16_384;
    private final ObjectMapper mapper;

    public CspReportController(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @PostMapping(consumes = {"application/csp-report", "application/reports+json", "application/json", "text/plain"})
    public ResponseEntity<Void> report(@RequestBody(required = false) String body) {
        if (body == null || body.isBlank() || body.length() > MAX_BODY) return ResponseEntity.noContent().build();
        try {
            JsonNode root = mapper.readTree(body);
            if (root.isArray()) {
                for (JsonNode r : root) logOne(r.path("body"));
            } else {
                logOne(root.has("csp-report") ? root.get("csp-report") : root);
            }
        } catch (Exception e) {
            log.debug("Informe CSP ilegible: {}", e.getMessage());
        }
        return ResponseEntity.noContent().build();
    }

    private void logOne(JsonNode r) {
        String directive = text(r, "effective-directive", "effectiveDirective", "violated-directive");
        String blocked = text(r, "blocked-uri", "blockedURL");
        String page = text(r, "document-uri", "documentURL");
        String sample = text(r, "script-sample", "sample");
        log.warn("CSP: {} bloquearía «{}» en {}{}", directive, blocked, page,
                sample.isEmpty() ? "" : " (muestra: " + sample + ")");
    }

    private static String text(JsonNode n, String... names) {
        for (String name : names) {
            String v = n.path(name).asText("");
            if (!v.isEmpty()) return v.length() > 200 ? v.substring(0, 200) + "…" : v;
        }
        return "";
    }
}
