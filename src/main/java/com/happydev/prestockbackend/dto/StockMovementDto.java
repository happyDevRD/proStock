package com.happydev.prestockbackend.dto;

import com.happydev.prestockbackend.entity.StockMovementType;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
public class StockMovementDto {
    private Long id;
    private Long productId;
    private LocalDateTime movementDate;
    private Integer quantityChange;
    private StockMovementType type;
    private String reason;
    private Long purchaseOrderId;
    private Long saleId;
    private Long userId;
    private Long sourceLocationId;
    private Long destinationLocationId;
    private String batchNumber;
    private LocalDateTime expirationDate;
    private BigDecimal unitCost;
    private Integer stockBefore;
    private Integer stockAfter;
}
