package com.happydev.prestockbackend.util;

import com.happydev.prestockbackend.entity.Product;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PromoPriceUtilsTest {

    @Test
    void effectiveSellingPrice_usesPromoPriceWhenActive() {
        Product p = new Product();
        p.setSellingPrice(new BigDecimal("100.00"));
        p.setPromoPrice(new BigDecimal("79.99"));
        p.setPromoStartDate(LocalDate.of(2026, 5, 1));
        p.setPromoEndDate(LocalDate.of(2026, 5, 31));
        assertEquals(new BigDecimal("79.99"), PromoPriceUtils.effectiveSellingPrice(p, LocalDate.of(2026, 5, 10)));
    }

    @Test
    void effectiveSellingPrice_percentOffWhenNoFixedPrice() {
        Product p = new Product();
        p.setSellingPrice(new BigDecimal("200.00"));
        p.setPromoPercentOff(new BigDecimal("10"));
        assertEquals(new BigDecimal("180.00"), PromoPriceUtils.effectiveSellingPrice(p, LocalDate.of(2026, 5, 7)));
    }

    @Test
    void effectiveSellingPrice_outsideScheduleFallsBackToList() {
        Product p = new Product();
        p.setSellingPrice(new BigDecimal("50.00"));
        p.setPromoPrice(new BigDecimal("40.00"));
        p.setPromoEndDate(LocalDate.of(2026, 4, 1));
        assertEquals(new BigDecimal("50.00"), PromoPriceUtils.effectiveSellingPrice(p, LocalDate.of(2026, 5, 7)));
    }
}
