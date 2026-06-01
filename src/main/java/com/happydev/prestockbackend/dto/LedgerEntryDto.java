package com.happydev.prestockbackend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LedgerEntryDto(
        LocalDate entryDate,
        String reference,
        String entryDescription,
        String lineDescription,
        BigDecimal debit,
        BigDecimal credit,
        BigDecimal runningBalance
) {}
