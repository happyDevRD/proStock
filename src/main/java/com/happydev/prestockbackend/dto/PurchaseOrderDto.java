package com.happydev.prestockbackend.dto;

import com.happydev.prestockbackend.entity.PaymentMethod;
import com.happydev.prestockbackend.entity.PurchaseOrderStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter @Setter
public class PurchaseOrderDto {
    private Long id;
    @NotNull
    private Long supplierId;
    @NotNull
    private LocalDate orderDate;
    private LocalDateTime receptionDate;
    @NotNull
    private PurchaseOrderStatus status;
    private List<PurchaseOrderItemDto> items;
    private BigDecimal paidAmount;
    private Double total;
    private String ncfProveedor;
    private String tipoBienesServicios;
    private BigDecimal totalItbis;
    private LocalDate fechaPago;
    private PaymentMethod paymentMethod;
}
