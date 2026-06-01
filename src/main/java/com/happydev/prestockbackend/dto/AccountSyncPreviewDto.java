package com.happydev.prestockbackend.dto;

import java.math.BigDecimal;

public record AccountSyncPreviewDto(
        int pendingSalesCount,
        int pendingPurchasesCount,
        BigDecimal totalSalesAmount,
        BigDecimal totalPurchasesAmount
) {}
