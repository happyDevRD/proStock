package com.happydev.prestockbackend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CreateQuoteRequest(
        Long customerId,
        LocalDate expiryDate,
        String notes,
        @NotEmpty List<@Valid QuoteItemRequest> items
) {
    public record QuoteItemRequest(
            Long productId,
            @NotBlank(message = "Cada línea necesita una descripción") String productName,
            @NotNull @PositiveOrZero(message = "El precio no puede ser negativo") BigDecimal unitPrice,
            @Min(value = 1, message = "La cantidad mínima es 1") int quantity,
            @PositiveOrZero(message = "El descuento no puede ser negativo") BigDecimal discount,
            /* Fracción (0.18 = 18 %); la columna es NUMERIC(5,4). */
            @NotNull
            @PositiveOrZero(message = "La tasa de ITBIS no puede ser negativa")
            @DecimalMax(value = "1", message = "La tasa de ITBIS va de 0 a 1 (0.18 = 18 %)")
            BigDecimal itbisRate
    ) {}
}
