package com.happydev.prestockbackend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record UpdateServiceOrderRequest(
        Long customerId,
        String title,
        LocalDateTime appointmentDate,
        LocalDate estimatedDelivery,
        BigDecimal estimatedAmount,
        BigDecimal depositAmount,
        String deviceBrand,
        String deviceModel,
        String deviceSerial,
        String deviceCondition,
        String problemDescription,
        String internalNotes
) {}
