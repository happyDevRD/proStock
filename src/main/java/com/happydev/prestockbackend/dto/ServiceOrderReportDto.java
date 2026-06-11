package com.happydev.prestockbackend.dto;

import com.happydev.prestockbackend.entity.ServiceOrderType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ServiceOrderReportDto(
        LocalDate startDate,
        LocalDate endDate,
        long created,
        long completed,
        long canceled,
        long activeNow,
        BigDecimal linkedSalesRevenue,
        double averageCompletionDays,
        List<ServiceOrderTypeReportRow> byType
) {
    public record ServiceOrderTypeReportRow(
            ServiceOrderType orderType,
            long created,
            long completed
    ) {}
}
