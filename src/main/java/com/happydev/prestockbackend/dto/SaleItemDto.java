package com.happydev.prestockbackend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter @Setter
public class SaleItemDto {
    private Long id;

    @NotNull(message = "Product ID cannot be null")
    private Long productId; // ID del producto

    @NotNull(message = "Quantity cannot be null")
    @Positive(message = "Quantity must be positive")
    private int quantity;

    /** Unidades devueltas acumuladas (notas de crédito). Solo lectura en respuestas. */
    private Integer quantityReturned;

    @NotNull(message = "Price cannot be null")
    @Positive(message = "Price must be positive")
    private BigDecimal unitPrice;

    /** Nombre del producto al momento de la venta (solo lectura en respuestas). */
    private String productName;

    private String productSku;
}
