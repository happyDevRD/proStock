package com.happydev.prestockbackend.dto;

import com.happydev.prestockbackend.entity.StockTransferStatus;

import java.time.LocalDateTime;
import java.util.List;

public record StockTransferDto(
        Long id,
        Long fromLocationId,
        String fromLocationName,
        Long toLocationId,
        String toLocationName,
        StockTransferStatus status,
        String notes,
        String createdByName,
        LocalDateTime createdAt,
        LocalDateTime completedAt,
        List<StockTransferItemDto> items
) {}
