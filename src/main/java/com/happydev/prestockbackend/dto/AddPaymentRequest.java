package com.happydev.prestockbackend.dto;

import com.happydev.prestockbackend.entity.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class AddPaymentRequest {
    @NotNull(message = "El monto es obligatorio")
    @DecimalMin(value = "0.01", message = "El monto debe ser mayor que cero")
    private BigDecimal amount;

    @NotNull(message = "El método de pago es obligatorio")
    private PaymentMethod paymentMethod;

    private String notes;
}
