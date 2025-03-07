package com.happydev.prestockbackend.dto;

import com.happydev.prestockbackend.entity.SaleStatus;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
public class SaleDto {
    private Long id;

    @NotNull(message = "Sale date cannot be null")
    private LocalDateTime saleDate;

    private Long customerId; // Usamos el ID del cliente

    @NotEmpty(message = "Sale items cannot be empty")
    private List<SaleItemDto> items;

    @NotNull
    private SaleStatus status;
}