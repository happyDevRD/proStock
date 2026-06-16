package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.entity.IntegrationCredential;
import com.happydev.prestockbackend.repository.IntegrationCredentialRepository;
import com.happydev.prestockbackend.security.TotpService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Fase Q3: lectura/escritura de credenciales de integración con cifrado en reposo. */
@Service
public class IntegrationCredentialService {

    private final IntegrationCredentialRepository repository;
    private final TotpService totpService; // reutiliza el cifrado simétrico de Encryptors.text()

    public IntegrationCredentialService(IntegrationCredentialRepository repository, TotpService totpService) {
        this.repository = repository;
        this.totpService = totpService;
    }

    /** Devuelve todas las credenciales de un proveedor con el valor descifrado. */
    @Transactional(readOnly = true)
    public List<CredentialEntry> listByProvider(String provider) {
        return repository.findByProvider(provider).stream()
                .map(c -> new CredentialEntry(c.getProvider(), c.getCredentialKey(),
                        totpService.decrypt(c.getEncryptedValue()), c.getUpdatedAt()))
                .toList();
    }

    /** Devuelve una credencial descifrada, o vacío si no existe. */
    @Transactional(readOnly = true)
    public Optional<String> get(String provider, String key) {
        return repository.findByProviderAndCredentialKey(provider, key)
                .map(c -> totpService.decrypt(c.getEncryptedValue()));
    }

    /** Crea o actualiza una credencial (upsert). */
    @Transactional
    public void set(String provider, String key, String plainValue) {
        IntegrationCredential credential = repository
                .findByProviderAndCredentialKey(provider, key)
                .orElseGet(() -> {
                    IntegrationCredential c = new IntegrationCredential();
                    c.setProvider(provider);
                    c.setCredentialKey(key);
                    return c;
                });
        credential.setEncryptedValue(totpService.encrypt(plainValue));
        credential.setUpdatedAt(LocalDateTime.now());
        repository.save(credential);
    }

    /** Elimina una credencial. No lanza error si no existe. */
    @Transactional
    public void delete(String provider, String key) {
        repository.deleteByProviderAndCredentialKey(provider, key);
    }

    public record CredentialEntry(String provider, String key, String value, LocalDateTime updatedAt) {}
}
