package com.happydev.prestockbackend.dto;

public record LocationStockItemDto(
        Long productId,
        String productName,
        String productCode,
        int quantity,
        Integer minStock
) {}
