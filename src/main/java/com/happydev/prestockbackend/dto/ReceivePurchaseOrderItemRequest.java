package com.happydev.prestockbackend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReceivePurchaseOrderItemRequest {

    @NotNull
    private Long itemId;

    @NotNull
    @Positive
    private Integer quantity;
}
