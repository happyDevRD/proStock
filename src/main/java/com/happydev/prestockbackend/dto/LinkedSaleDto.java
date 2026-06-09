package com.happydev.prestockbackend.dto;

import com.happydev.prestockbackend.entity.SaleStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record LinkedSaleDto(
        Long id,
        String ncf,
        LocalDateTime saleDate,
        SaleStatus status,
        BigDecimal montoTotal,
        BigDecimal paidAmount
) {}
