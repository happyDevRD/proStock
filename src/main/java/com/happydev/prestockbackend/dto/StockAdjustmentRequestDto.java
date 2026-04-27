package com.happydev.prestockbackend.dto;

import com.happydev.prestockbackend.entity.StockMovementType;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
public class StockAdjustmentRequestDto {

    @NotNull
    private Integer quantityChange;

    @NotNull
    private StockMovementType type;

    private String reason;
    private String batchNumber;
    private LocalDateTime expirationDate;
    private BigDecimal unitCost;
    private Long sourceLocationId;
    private Long destinationLocationId;
}
