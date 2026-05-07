package com.happydev.prestockbackend.util;

import com.happydev.prestockbackend.entity.Product;
import org.springframework.lang.NonNull;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

public final class PromoPriceUtils {

    private PromoPriceUtils() {
    }

    public static boolean isPromoScheduleActive(
            LocalDate promoStart,
            LocalDate promoEnd,
            @NonNull LocalDate today
    ) {
        if (promoStart != null && today.isBefore(promoStart)) {
            return false;
        }
        if (promoEnd != null && today.isAfter(promoEnd)) {
            return false;
        }
        return true;
    }

    public static boolean hasPromoPricing(@NonNull Product product) {
        if (product.getPromoPrice() != null && product.getPromoPrice().compareTo(BigDecimal.ZERO) > 0) {
            return true;
        }
        return product.getPromoPercentOff() != null && product.getPromoPercentOff().compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Precio de venta aplicando promoción vigente; si no hay promoción activa, devuelve el precio de lista.
     */
    public static BigDecimal effectiveSellingPrice(@NonNull Product product, @NonNull LocalDate today) {
        BigDecimal list = product.getSellingPrice() != null ? product.getSellingPrice() : BigDecimal.ZERO;
        if (!hasPromoPricing(product)
                || !isPromoScheduleActive(product.getPromoStartDate(), product.getPromoEndDate(), today)) {
            return list.setScale(2, RoundingMode.HALF_UP);
        }
        if (product.getPromoPrice() != null && product.getPromoPrice().compareTo(BigDecimal.ZERO) > 0) {
            return product.getPromoPrice().setScale(2, RoundingMode.HALF_UP);
        }
        if (product.getPromoPercentOff() != null && product.getPromoPercentOff().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal pct = product.getPromoPercentOff().min(new BigDecimal("100"));
            BigDecimal factor = BigDecimal.ONE.subtract(
                    pct.divide(new BigDecimal("100"), 8, RoundingMode.HALF_UP)
            );
            return list.multiply(factor).setScale(2, RoundingMode.HALF_UP);
        }
        return list.setScale(2, RoundingMode.HALF_UP);
    }
}
