package com.happydev.prestockbackend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record JournalEntryDto(
        Long id,
        LocalDate entryDate,
        String reference,
        String description,
        String entryType,
        String status,
        String createdBy,
        LocalDateTime createdAt,
        List<JournalEntryLineDto> lines,
        BigDecimal totalDebit,
        BigDecimal totalCredit
) {}
