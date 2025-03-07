package com.happydev.prestockbackend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import lombok.Getter;
import lombok.Setter;


@Getter @Setter
public class SaleItemDto {
    private Long id;

    @NotNull(message = "Product ID cannot be null")
    private Long productId; // ID del producto

    @NotNull(message = "Quantity cannot be null")
    @Positive(message = "Quantity must be positive")
    private int quantity;

    @NotNull(message = "Price cannot be null")
    @Positive(message = "Price must be positive")
    private double unitPrice; // Precio unitario al momento de la venta
}
