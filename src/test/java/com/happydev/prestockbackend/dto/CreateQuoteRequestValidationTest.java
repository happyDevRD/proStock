package com.happydev.prestockbackend.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Las líneas se validan (antes una tasa de 18 llegaba a la BD y salía un error genérico de integridad). */
class CreateQuoteRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private static CreateQuoteRequest withRate(String rate) {
        var item = new CreateQuoteRequest.QuoteItemRequest(null, "Sesión", new BigDecimal("1000"), 1,
                BigDecimal.ZERO, new BigDecimal(rate));
        return new CreateQuoteRequest(null, null, null, List.of(item));
    }

    @Test
    void tasaFraccionariaEsValida() {
        assertThat(validator.validate(withRate("0.18"))).isEmpty();
        assertThat(validator.validate(withRate("0"))).isEmpty();
    }

    @Test
    void tasaComoPorcentajeSeRechazaConMensajeClaro() {
        var errors = validator.validate(withRate("18"));
        assertThat(errors).extracting(v -> v.getMessage()).containsExactly("La tasa de ITBIS va de 0 a 1 (0.18 = 18 %)");
    }

    @Test
    void precioCeroSigueSiendoValido() {
        var item = new CreateQuoteRequest.QuoteItemRequest(null, "Cortesía", BigDecimal.ZERO, 1, null, BigDecimal.ZERO);
        assertThat(validator.validate(new CreateQuoteRequest(null, null, null, List.of(item)))).isEmpty();
    }
}
