package com.happydev.prestockbackend.dto;

import java.math.BigDecimal;

public record QuoteItemDto(
        Long id,
        Long productId,
        String productName,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal discount,
        BigDecimal itbisRate,
        BigDecimal subtotal
) {}
