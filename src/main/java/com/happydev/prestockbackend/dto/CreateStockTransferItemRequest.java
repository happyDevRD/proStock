package com.happydev.prestockbackend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreateStockTransferItemRequest(
        @NotNull Long productId,
        @NotNull @Min(1) int quantity
) {}
