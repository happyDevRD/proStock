package com.happydev.prestockbackend.dto;

import java.math.BigDecimal;

public record ServiceOrderItemDto(
        Long id,
        Long productId,
        String productName,
        String productSku,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal,
        String notes
) {}
