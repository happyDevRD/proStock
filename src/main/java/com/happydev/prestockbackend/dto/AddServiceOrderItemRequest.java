package com.happydev.prestockbackend.dto;

import java.math.BigDecimal;

public record AddServiceOrderItemRequest(
        Long productId,
        Integer quantity,
        BigDecimal unitPrice,
        String notes
) {}
