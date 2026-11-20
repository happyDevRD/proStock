package com.happydev.prestockbackend.controller;

import com.happydev.prestockbackend.calendar.GoogleCalendarSync;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Estado y prueba de la integración con Google Calendar (Configuración → Integraciones). */
@RestController
@RequestMapping("/api/integrations/google-calendar")
@PreAuthorize("hasAnyRole('ADMIN','GESTOR')")
public class GoogleCalendarController {

    private final GoogleCalendarSync sync;

    public GoogleCalendarController(GoogleCalendarSync sync) {
        this.sync = sync;
    }

    @GetMapping("/status")
    public GoogleCalendarSync.Status status() {
        return sync.status();
    }

    /** Comprueba la llave y el acceso al calendario; responde con el nombre del calendario. */
    @PostMapping("/test")
    public ResponseEntity<Map<String, String>> test() {
        return ResponseEntity.ok(Map.of("calendar", sync.test()));
    }

    /** Sincroniza ya lo pendiente (sin esperar a la vuelta de cada minuto). */
    @PostMapping("/sync")
    public GoogleCalendarSync.Status syncNow() {
        sync.syncPending();
        return sync.status();
    }
}
