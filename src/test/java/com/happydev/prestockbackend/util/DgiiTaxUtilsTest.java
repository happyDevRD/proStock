package com.happydev.prestockbackend.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DgiiTaxUtilsTest {

    @Test
    void roundMoney_WhenThirdDecimalIsFive_RoundsUp() {
        BigDecimal value = new BigDecimal("10.125");
        BigDecimal rounded = DgiiTaxUtils.roundMoney(value);
        assertEquals(new BigDecimal("10.13"), rounded);
    }

    @Test
    void roundMoney_WhenThirdDecimalIsLessThanFive_RoundsDown() {
        BigDecimal value = new BigDecimal("10.124");
        BigDecimal rounded = DgiiTaxUtils.roundMoney(value);
        assertEquals(new BigDecimal("10.12"), rounded);
    }
}
