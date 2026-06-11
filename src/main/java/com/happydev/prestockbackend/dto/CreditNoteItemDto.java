package com.happydev.prestockbackend.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class CreditNoteItemDto {
    private Long id;
    private Long saleItemId;
    private Long productId;
    private int quantity;
    private BigDecimal unitPrice;
    private String productName;
    private String productSku;
}
