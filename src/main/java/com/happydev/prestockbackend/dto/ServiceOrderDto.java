package com.happydev.prestockbackend.dto;

import com.happydev.prestockbackend.entity.ServiceOrderStatus;
import com.happydev.prestockbackend.entity.ServiceOrderType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ServiceOrderDto(
        Long id,
        String orderNumber,
        Long customerId,
        String customerName,
        ServiceOrderType orderType,
        String title,
        ServiceOrderStatus status,
        String currentStage,
        LocalDateTime appointmentDate,
        LocalDate estimatedDelivery,
        BigDecimal estimatedAmount,
        boolean budgetApproved,
        LocalDateTime budgetApprovedAt,
        BigDecimal depositAmount,
        // Repair device
        String deviceBrand,
        String deviceModel,
        String deviceSerial,
        String deviceCondition,
        String problemDescription,
        // General
        String internalNotes,
        String createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        // Detail (populated only on single-order GET)
        List<ServiceOrderStageDto> stages,
        List<ServiceOrderNoteDto> notes,
        List<LinkedSaleDto> linkedSales,
        List<ServiceOrderItemDto> items
) {}
