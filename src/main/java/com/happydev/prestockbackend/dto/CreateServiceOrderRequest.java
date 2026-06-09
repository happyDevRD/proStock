package com.happydev.prestockbackend.dto;

import com.happydev.prestockbackend.entity.ServiceOrderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record CreateServiceOrderRequest(
        Long customerId,
        @NotNull ServiceOrderType orderType,
        @NotBlank String title,
        LocalDateTime appointmentDate,
        LocalDate estimatedDelivery,
        BigDecimal estimatedAmount,
        BigDecimal depositAmount,
        // Repair
        String deviceBrand,
        String deviceModel,
        String deviceSerial,
        String deviceCondition,
        String problemDescription,
        // General
        String internalNotes
) {}
