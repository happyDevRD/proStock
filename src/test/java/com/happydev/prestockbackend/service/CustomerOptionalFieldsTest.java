package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.dto.CustomerDto;
import com.happydev.prestockbackend.entity.TipoIdentificacion;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Cédula y correo son opcionales al crear clientes (se pueden completar luego por WhatsApp). */
class CustomerOptionalFieldsTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private static CustomerDto base() {
        CustomerDto dto = new CustomerDto();
        dto.setFirstName("Ana");
        dto.setLastName("Pérez");
        dto.setPhoneNumber("8095551234");
        return dto;
    }

    @Test
    void soloNombreYApellidoEsValido() {
        assertThat(validator.validate(base())).isEmpty();
    }

    @Test
    void vaciosSeGuardanComoNull() {
        CustomerDto dto = base();
        dto.setEmail("  ");
        dto.setRncCedula("");
        assertThat(dto.getEmail()).isNull();
        assertThat(dto.getRncCedula()).isNull();
        assertThat(validator.validate(dto)).isEmpty();
    }

    @Test
    void cedulaConGuionesSeNormalizaYSeValida() {
        CustomerDto dto = base();
        dto.setRncCedula("001-1234567-8");
        assertThat(dto.getRncCedula()).isEqualTo("00112345678");
        assertThat(validator.validate(dto)).isEmpty();

        dto.setRncCedula("123");
        assertThat(validator.validate(dto)).isNotEmpty();
    }

    @Test
    void correoMalFormadoSigueRechazandose() {
        CustomerDto dto = base();
        dto.setEmail("no-es-correo");
        assertThat(validator.validate(dto)).isNotEmpty();
    }

    @Test
    void tipoSeDeducePorLaCantidadDeDigitos() {
        assertThat(CustomerServiceImpl.resolveTipo(null, null)).isNull();
        assertThat(CustomerServiceImpl.resolveTipo(null, TipoIdentificacion.RNC)).isEqualTo(TipoIdentificacion.RNC);
        assertThat(CustomerServiceImpl.resolveTipo("00112345678", null)).isEqualTo(TipoIdentificacion.CEDULA);
        assertThat(CustomerServiceImpl.resolveTipo("131234567", null)).isEqualTo(TipoIdentificacion.RNC);
        assertThat(CustomerServiceImpl.resolveTipo("131234567", TipoIdentificacion.PASAPORTE_EXTRANJERO))
                .isEqualTo(TipoIdentificacion.PASAPORTE_EXTRANJERO);
    }

    @Test
    void pasaporteAlfanumericoSeAceptaSoloConSuTipo() {
        CustomerDto dto = base();
        dto.setTipoIdentificacion(TipoIdentificacion.PASAPORTE_EXTRANJERO);
        dto.setRncCedula(" ab-123 456 ");
        assertThat(dto.getRncCedula()).isEqualTo("AB123456");
        assertThat(validator.validate(dto)).isEmpty();

        dto.setRncCedula("A1");
        assertThat(validator.validate(dto)).as("muy corto").isNotEmpty();
        dto.setRncCedula("AB12345678901234567890");
        assertThat(validator.validate(dto)).as("más de 20").isNotEmpty();
        dto.setRncCedula("AB12#45");
        assertThat(validator.validate(dto)).as("símbolos").isNotEmpty();

        dto.setTipoIdentificacion(TipoIdentificacion.CEDULA);
        dto.setRncCedula("AB123456");
        assertThat(validator.validate(dto)).as("letras con tipo cédula").isNotEmpty();
    }

    @Test
    void documentoConLetrasSinTipoSeDeduceComoPasaporte() {
        assertThat(CustomerServiceImpl.resolveTipo("AB123456", null)).isEqualTo(TipoIdentificacion.PASAPORTE_EXTRANJERO);
    }

    @Test
    void laEntidadValidaIgual() {
        com.happydev.prestockbackend.entity.Customer c = new com.happydev.prestockbackend.entity.Customer();
        c.setFirstName("John");
        c.setLastName("Smith");
        c.setTipoIdentificacion(TipoIdentificacion.PASAPORTE_EXTRANJERO);
        c.setRncCedula("X1234567");
        assertThat(validator.validate(c)).isEmpty();
        c.setTipoIdentificacion(TipoIdentificacion.RNC);
        assertThat(validator.validate(c)).isNotEmpty();
    }
}
