package com.happydev.prestockbackend.dto;

import java.math.BigDecimal;

public record SaleSummaryDayDto(
        String date,
        long count,
        BigDecimal revenue,
        BigDecimal tax
) {
}
