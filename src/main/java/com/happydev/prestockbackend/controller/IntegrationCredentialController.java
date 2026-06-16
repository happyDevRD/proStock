package com.happydev.prestockbackend.controller;

import com.happydev.prestockbackend.service.IntegrationCredentialService;
import com.happydev.prestockbackend.service.IntegrationCredentialService.CredentialEntry;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Fase Q3: gestión de credenciales de integraciones (solo ADMIN/GESTOR).
 * Todas las respuestas devuelven el valor descifrado — protegido por roles de admin.
 */
@RestController
@RequestMapping("/api/integrations")
@PreAuthorize("hasAnyRole('ADMIN','GESTOR')")
public class IntegrationCredentialController {

    private final IntegrationCredentialService service;

    public IntegrationCredentialController(IntegrationCredentialService service) {
        this.service = service;
    }

    /** Lista todas las credenciales de un proveedor. */
    @GetMapping("/{provider}/credentials")
    public List<CredentialResponse> list(@PathVariable String provider) {
        return service.listByProvider(provider).stream()
                .map(e -> new CredentialResponse(e.provider(), e.key(), e.value(), e.updatedAt()))
                .toList();
    }

    /** Crea o actualiza una credencial. */
    @PutMapping("/{provider}/credentials/{key}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void set(
            @PathVariable String provider,
            @PathVariable String key,
            @RequestBody SetCredentialRequest body
    ) {
        service.set(provider, key, body.value());
    }

    /** Elimina una credencial. */
    @DeleteMapping("/{provider}/credentials/{key}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String provider, @PathVariable String key) {
        service.delete(provider, key);
    }

    record CredentialResponse(String provider, String key, String value, LocalDateTime updatedAt) {}

    record SetCredentialRequest(@NotBlank @Size(max = 2048) String value) {}
}
