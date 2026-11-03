package com.happydev.prestockbackend.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Montos en formato dominicano para textos: "RD$124,903.00" (coma de miles, punto decimal). */
public final class MoneyFormatUtils {

    private MoneyFormatUtils() {
    }

    /** "124,903.00", sin depender del Locale del servidor. */
    public static String amount(BigDecimal value) {
        BigDecimal safe = value != null ? value : BigDecimal.ZERO;
        // DecimalFormat no es thread-safe: una instancia por llamada
        DecimalFormat format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
        return format.format(safe.setScale(2, RoundingMode.HALF_UP));
    }

    /** "RD$124,903.00" */
    public static String rd(BigDecimal value) {
        return "RD$" + amount(value);
    }

    /** "RD$124,903.00" */
    public static String rd(double value) {
        return rd(BigDecimal.valueOf(value));
    }
}
