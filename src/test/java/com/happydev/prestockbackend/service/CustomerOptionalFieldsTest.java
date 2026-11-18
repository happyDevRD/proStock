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
}
