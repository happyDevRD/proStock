package com.happydev.prestockbackend.dto;

import jakarta.validation.Valid;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class ReceivePurchaseOrderRequest {

    @Valid
    private List<ReceivePurchaseOrderItemRequest> items = new ArrayList<>();
}
