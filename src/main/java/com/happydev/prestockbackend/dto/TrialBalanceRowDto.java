package com.happydev.prestockbackend.dto;

import java.math.BigDecimal;

public record TrialBalanceRowDto(
        String code,
        String name,
        String type,
        BigDecimal totalDebit,
        BigDecimal totalCredit,
        BigDecimal balance
) {}
