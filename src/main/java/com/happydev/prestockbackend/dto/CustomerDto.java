package com.happydev.prestockbackend.dto;

import com.happydev.prestockbackend.entity.TipoIdentificacion;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.happydev.prestockbackend.entity.Customer;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CustomerDto {
    private Long id;

    @NotBlank(message = "First name is required")
    @Size(min = 2, max = 50)
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(min = 2, max = 50)
    private String lastName;

    /** Opcional: muchos clientes no lo dan al agendar (se puede pedir luego por WhatsApp). */
    @Email(message = "Invalid email format")
    private String email;

    private String phoneNumber;
    private String address;

    /** Opcional; 9 (RNC) u 11 (cédula) dígitos, o pasaporte alfanumérico si el tipo es PASAPORTE_EXTRANJERO. */
    private String rncCedula;

    /** Opcional; si hay RNC/cédula y no viene, se deduce por la cantidad de dígitos. */
    private TipoIdentificacion tipoIdentificacion;

    private boolean portalEnabled;

    /** Acepta avisos por WhatsApp; null = no se le ha preguntado (no se le envía nada). */
    private Boolean whatsappOptIn;

    /** Solo lectura: cuándo y cómo dio (o retiró) el consentimiento. */
    private java.time.Instant whatsappOptInAt;
    private String whatsappOptInSource;

    /** Vacío = sin correo (null), para no chocar con la restricción UNIQUE. */
    public void setEmail(String email) {
        this.email = email == null || email.isBlank() ? null : email.trim();
    }

    /** Vacío = sin identificación; se quitan guiones y espacios y se pasa a mayúsculas (pasaportes). */
    public void setRncCedula(String rncCedula) {
        String clean = rncCedula == null ? "" : rncCedula.replaceAll("[\\s-]", "").toUpperCase(java.util.Locale.ROOT);
        this.rncCedula = clean.isEmpty() ? null : clean;
    }

    @JsonIgnore
    @AssertTrue(message = "RNC/Cédula debe tener 9 u 11 dígitos; el pasaporte, de 5 a 20 letras o números")
    public boolean isRncCedulaValid() {
        return Customer.isValidDocument(rncCedula, tipoIdentificacion);
    }
}
