package com.happydev.prestockbackend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CreateQuoteRequest(
        Long customerId,
        LocalDate expiryDate,
        String notes,
        @NotEmpty List<QuoteItemRequest> items
) {
    public record QuoteItemRequest(
            Long productId,
            @NotBlank String productName,
            @NotNull @Positive BigDecimal unitPrice,
            @Min(1) int quantity,
            @PositiveOrZero BigDecimal discount,
            @NotNull @PositiveOrZero BigDecimal itbisRate
    ) {}
}
