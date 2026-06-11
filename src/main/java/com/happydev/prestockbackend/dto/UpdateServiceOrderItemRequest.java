package com.happydev.prestockbackend.dto;

import jakarta.validation.constraints.Min;

import java.math.BigDecimal;

public record UpdateServiceOrderItemRequest(
        @Min(value = 1, message = "quantity debe ser al menos 1")
        Integer quantity,
        BigDecimal unitPrice,
        String notes
) {}
