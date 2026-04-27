package com.happydev.prestockbackend.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class DgiiTaxUtils {
    private static final int MONEY_SCALE = 2;

    private DgiiTaxUtils() {
    }

    public static BigDecimal roundMoney(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
