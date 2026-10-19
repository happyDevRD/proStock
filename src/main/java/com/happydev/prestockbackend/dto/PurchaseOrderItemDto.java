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

    private int quantityReceived;

    /** Costo unitario sin ITBIS. Obligatorio y mayor que cero: un nombre de campo equivocado no debe guardar 0. */
    @Positive(message = "El costo unitario de cada línea debe ser mayor que cero.")
    private double unitPrice;

}