package com.happydev.prestockbackend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Cifras de un cliente sobre todas sus ventas abonadas o completadas (no las pendientes
 * ni las canceladas): total comprado, saldo por cobrar, número de facturas y última compra.
 */
public record CustomerStatsDto(
        Long customerId,
        BigDecimal totalSpent,
        BigDecimal pendingBalance,
        long invoiceCount,
        LocalDateTime lastPurchaseDate
) {}
