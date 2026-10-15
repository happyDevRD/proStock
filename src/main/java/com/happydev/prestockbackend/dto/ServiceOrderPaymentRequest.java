package com.happydev.prestockbackend.dto;

import com.happydev.prestockbackend.entity.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

/**
 * Cobro de una orden de servicio: anticipo ({@code amount} obligatorio, monto libre con ITBIS incluido)
 * o factura del saldo ({@code amount} se ignora: se cobra el saldo completo).
 * {@code tipoComprobante} vacío = 31 si el cliente tiene RNC, 32 en otro caso.
 */
public record ServiceOrderPaymentRequest(
        @DecimalMin(value = "0.01", message = "El monto debe ser mayor que cero") BigDecimal amount,
        @NotNull(message = "El método de pago es obligatorio") PaymentMethod paymentMethod,
        @Pattern(regexp = "^(31|32)?$", message = "Tipo de comprobante inválido (31 o 32)") String tipoComprobante,
        String notes
) {}
