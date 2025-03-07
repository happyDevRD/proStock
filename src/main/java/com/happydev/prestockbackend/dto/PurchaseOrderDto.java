package com.happydev.prestockbackend.dto;

import com.happydev.prestockbackend.entity.PurchaseOrderStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter @Setter
public class PurchaseOrderDto {
    private Long id;
    @NotNull
    private Long supplierId; // ID del proveedor, como en ProductDto
    @NotNull
    private LocalDate orderDate;
    private LocalDateTime receptionDate;
    @NotNull
    private PurchaseOrderStatus status;
    private List<PurchaseOrderItemDto> items; // Usaremos un DTO para los ítems
}
