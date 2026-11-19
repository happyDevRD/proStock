package com.happydev.prestockbackend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(name = "customers")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Customer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(unique = true) // Asegura que el correo electrónico sea único
    private String email;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column
    private String address; // Podrías tener una entidad Address separada

    /** 9 (RNC) u 11 (cédula) dígitos; pasaporte: 5–20 letras o números (ver {@link #isValidDocument}). */
    @Column(name = "rnc_cedula", length = 20)
    private String rncCedula;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_identificacion", length = 30)
    private TipoIdentificacion tipoIdentificacion;

    @Column(name = "idempotency_key", length = 64)
    private String idempotencyKey;

    @Column(name = "portal_enabled", nullable = false)
    private boolean portalEnabled = false;

    @Column(name = "portal_password")
    private String portalPassword;

    @Column(name = "portal_last_login")
    private LocalDateTime portalLastLogin;

    /** Acepta avisos por WhatsApp; null = no se le ha preguntado (no se le envía nada). */
    @Column(name = "whatsapp_opt_in")
    private Boolean whatsappOptIn;

    @Column(name = "whatsapp_opt_in_at")
    private Instant whatsappOptInAt;

    /** "web" (formulario de la web) o "staff" (lo marcó el estudio) */
    @Column(name = "whatsapp_opt_in_source", length = 20)
    private String whatsappOptInSource;

    private static final java.util.regex.Pattern DIGITS_9_OR_11 = java.util.regex.Pattern.compile("^\\d{9}(\\d{2})?$");
    private static final java.util.regex.Pattern PASSPORT = java.util.regex.Pattern.compile("^[A-Z0-9]{5,20}$");

    /** Sin documento es válido; pasaporte = alfanumérico en mayúsculas; el resto, 9 u 11 dígitos. */
    public static boolean isValidDocument(String doc, TipoIdentificacion tipo) {
        if (doc == null || doc.isEmpty()) return true;
        if (tipo == TipoIdentificacion.PASAPORTE_EXTRANJERO) return PASSPORT.matcher(doc).matches();
        return DIGITS_9_OR_11.matcher(doc).matches();
    }

    @jakarta.validation.constraints.AssertTrue(message = "RNC/Cédula debe tener 9 u 11 dígitos; el pasaporte, de 5 a 20 letras o números")
    public boolean isRncCedulaValid() {
        return isValidDocument(rncCedula, tipoIdentificacion);
    }
}
