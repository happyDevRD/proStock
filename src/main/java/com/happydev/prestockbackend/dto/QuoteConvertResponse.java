package com.happydev.prestockbackend.dto;

import java.math.BigDecimal;
import java.util.List;

public record QuoteConvertResponse(
        Long quoteId,
        Long customerId,
        List<QuoteItemForPos> items,
        BigDecimal suggestedAmount
) {
    public record QuoteItemForPos(Long productId, int quantity, BigDecimal unitPrice) {}
}
