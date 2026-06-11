package com.happydev.prestockbackend.dto;

import java.math.BigDecimal;

public record ServiceOrderItemDto(
        Long id,
        Long productId,
        String productName,
        String productSku,
        Integer quantity,
        Integer invoicedQuantity,
        Integer pendingQuantity,
        BigDecimal unitPrice,
        BigDecimal subtotal,
        BigDecimal pendingSubtotal,
        String notes
) {}
