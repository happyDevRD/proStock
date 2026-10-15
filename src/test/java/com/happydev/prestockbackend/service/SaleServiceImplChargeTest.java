package com.happydev.prestockbackend.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SaleServiceImplChargeTest {

    private static final BigDecimal RATE = new BigDecimal("0.18");

    /** El anticipo cobrado (ITBIS incluido) debe reconstruirse exacto: base + ITBIS(base) = monto. */
    @ParameterizedTest
    @ValueSource(strings = {"5000.00", "9200.00", "12345.67", "0.01", "1.00", "21830.00", "99999.99", "333.33"})
    void baseForInclusiveAmount_rebuildsExactAmount(String raw) {
        BigDecimal amount = new BigDecimal(raw);
        BigDecimal base = SaleServiceImpl.baseForInclusiveAmount(amount, RATE);
        BigDecimal total = base.add(base.multiply(RATE).setScale(2, RoundingMode.HALF_UP));
        assertEquals(0, amount.compareTo(total), "base " + base + " → total " + total);
    }

    /** Montos sin base exacta (p. ej. 2,000.00): la base es la más cercana y el ITBIS absorbe el céntimo. */
    @ParameterizedTest
    @ValueSource(strings = {"2000.00", "2000.06"})
    void baseForInclusiveAmount_unreachableAmountStaysWithinOneCent(String raw) {
        BigDecimal amount = new BigDecimal(raw);
        BigDecimal base = SaleServiceImpl.baseForInclusiveAmount(amount, RATE);
        BigDecimal total = base.add(base.multiply(RATE).setScale(2, RoundingMode.HALF_UP));
        assertEquals(true, amount.subtract(total).abs().compareTo(new BigDecimal("0.01")) <= 0, "total " + total);
    }

    @ParameterizedTest
    @ValueSource(strings = {"100.00", "2500.50"})
    void baseForInclusiveAmount_exemptReturnsSameAmount(String raw) {
        BigDecimal amount = new BigDecimal(raw);
        assertEquals(amount, SaleServiceImpl.baseForInclusiveAmount(amount, BigDecimal.ZERO));
    }
}
