package com.happydev.prestockbackend.dto;

import com.happydev.prestockbackend.entity.ExpenseCategory;
import com.happydev.prestockbackend.entity.PaymentMethod;
import com.happydev.prestockbackend.entity.TipoIdentificacion;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class ExpenseDto {
    private Long id;
    private String description;
    private BigDecimal amount;
    private BigDecimal itbis;
    private LocalDate expenseDate;
    private LocalDate paymentDate;
    private PaymentMethod paymentMethod;
    private ExpenseCategory category;
    private Long supplierId;
    private String supplierName;
    private String ncfProveedor;
    private String tipoBienesServicios;
    private String rncCedula;
    private TipoIdentificacion tipoIdentificacion;
    private String notes;
    private String createdBy;
    private LocalDateTime createdAt;
}
