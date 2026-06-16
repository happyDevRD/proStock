package com.happydev.prestockbackend.dto;

public record StockTransferItemDto(
        Long id,
        Long productId,
        String productName,
        String productCode,
        int quantity
) {}
