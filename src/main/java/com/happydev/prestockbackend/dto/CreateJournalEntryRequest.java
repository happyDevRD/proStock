package com.happydev.prestockbackend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CreateJournalEntryRequest(
        LocalDate entryDate,
        String reference,
        String description,
        List<LineRequest> lines
) {
    public record LineRequest(
            Long accountId,
            BigDecimal debit,
            BigDecimal credit,
            String description
    ) {}
}
