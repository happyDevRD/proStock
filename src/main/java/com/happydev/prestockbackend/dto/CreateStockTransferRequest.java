package com.happydev.prestockbackend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreateStockTransferRequest(
        Long fromLocationId,
        @NotNull Long toLocationId,
        @NotEmpty @Valid List<CreateStockTransferItemRequest> items,
        String notes
) {}
