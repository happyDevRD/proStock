package com.happydev.prestockbackend.dto;

import com.happydev.prestockbackend.entity.PaymentMethod;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
public class SupplierPaymentDto {
    private Long id;
    private Long purchaseOrderId;
    private LocalDateTime paymentDate;
    private BigDecimal amount;
    private PaymentMethod paymentMethod;
    private String notes;
    private String createdBy;
    private LocalDateTime createdAt;
}
