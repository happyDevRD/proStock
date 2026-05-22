package com.happydev.prestockbackend.dto;

import java.math.BigDecimal;
import java.util.List;

public record SaleSummaryDto(
        long totalCount,
        long completedCount,
        long pendingCount,
        long canceledCount,
        BigDecimal completedRevenue,
        BigDecimal completedTax,
        BigDecimal averageTicket,
        double completionRate,
        long todayCompletedCount,
        BigDecimal todayRevenue,
        BigDecimal todayAverageTicket,
        List<SaleSummaryDayDto> topDaysByRevenue,
        List<SaleSummaryDayDto> revenueTrend
) {
}
