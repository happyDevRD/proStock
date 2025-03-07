package com.happydev.prestockbackend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PurchaseOrderItemDto {

    private Long id;

    @NotNull
    private Long productId; // ID del producto, como en ProductDto

    @NotNull
    @Positive
    private int quantity;

    private double unitPrice; //Se podría omitir, si se trae del producto, pero es mejor tenerlo.

}