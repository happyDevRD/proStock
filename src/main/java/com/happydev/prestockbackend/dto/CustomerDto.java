package com.happydev.prestockbackend.dto;

import com.happydev.prestockbackend.entity.TipoIdentificacion;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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

    /** Opcional; si viene, 9 (RNC) u 11 (cédula) dígitos. */
    @Pattern(regexp = "^\\d{9}(\\d{2})?$", message = "RNC/Cedula debe tener 9 u 11 digitos")
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

    /** Vacío = sin identificación; se quitan guiones y espacios. */
    public void setRncCedula(String rncCedula) {
        String digits = rncCedula == null ? "" : rncCedula.replaceAll("[\\s-]", "");
        this.rncCedula = digits.isEmpty() ? null : digits;
    }
}
