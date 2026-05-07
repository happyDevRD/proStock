package com.happydev.prestockbackend.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
public class BulkProductPromoRequest {

    @NotEmpty(message = "Debe indicar al menos un producto")
    private List<Long> productIds;

    /**
     * Si es true, elimina toda promoción en los productos indicados (ignora el resto de campos).
     */
    private boolean clearPromotions;

    private BigDecimal promoPrice;

    private BigDecimal promoPercentOff;

    private LocalDate promoStartDate;
    private LocalDate promoEndDate;
}
