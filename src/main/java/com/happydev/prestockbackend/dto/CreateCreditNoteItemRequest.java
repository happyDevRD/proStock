package com.happydev.prestockbackend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateCreditNoteItemRequest {
    @NotNull
    private Long saleItemId;

    @NotNull
    @Positive
    private Integer quantity;
}
