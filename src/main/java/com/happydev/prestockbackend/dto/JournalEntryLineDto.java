package com.happydev.prestockbackend.dto;

import java.math.BigDecimal;

public record JournalEntryLineDto(
        Long id,
        Long accountId,
        String accountCode,
        String accountName,
        BigDecimal debit,
        BigDecimal credit,
        String description
) {}
