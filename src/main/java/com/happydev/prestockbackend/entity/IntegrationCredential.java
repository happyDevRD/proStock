package com.happydev.prestockbackend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Fase Q3: credencial de integración (p.ej. token de WhatsApp, API key de pasarela de pago) cifrada en reposo. */
@Entity
@Table(
        name = "integration_credentials",
        uniqueConstraints = @UniqueConstraint(columnNames = {"provider", "credential_key"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class IntegrationCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Nombre del proveedor (p.ej. "whatsapp", "azul", "cardnet", "pixelpay"). */
    @Column(nullable = false, length = 64)
    private String provider;

    /** Clave de la credencial dentro del proveedor (p.ej. "access_token", "api_key"). */
    @Column(name = "credential_key", nullable = false, length = 128)
    private String credentialKey;

    /** Valor cifrado con {@code TotpService} (misma clave de cifrado). */
    @Column(name = "encrypted_value", nullable = false, columnDefinition = "TEXT")
    private String encryptedValue;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
