package com.happydev.prestockbackend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record QuoteDto(
        Long id,
        Long customerId,
        String customerName,
        LocalDateTime quoteDate,
        LocalDate expiryDate,
        String status,
        String notes,
        BigDecimal subtotal,
        BigDecimal totalItbis,
        BigDecimal discountAmount,
        BigDecimal montoTotal,
        Long convertedSaleId,
        boolean expired,
        List<QuoteItemDto> items,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
