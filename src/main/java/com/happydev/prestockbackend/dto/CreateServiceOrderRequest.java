package com.happydev.prestockbackend.dto;

import com.happydev.prestockbackend.entity.ServiceOrderType;
import jakarta.validation.constraints.NotBlank;
import org.springframework.lang.Nullable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record CreateServiceOrderRequest(
        Long customerId,
        @Nullable ServiceOrderType orderType,
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
        String internalNotes,
        // Fotografía: empleados asignados
        Long photographerId,
        Long editorId,
        // Fin de la cita (exclusivo) y día completo; sin fin la cita dura 1 hora
        LocalDateTime appointmentEnd,
        Boolean appointmentAllDay
) {
    /** Sin duración: la cita (si hay) dura lo predeterminado. */
    public CreateServiceOrderRequest(Long customerId, ServiceOrderType orderType, String title,
                                     LocalDateTime appointmentDate, LocalDate estimatedDelivery,
                                     BigDecimal estimatedAmount, BigDecimal depositAmount,
                                     String deviceBrand, String deviceModel, String deviceSerial,
                                     String deviceCondition, String problemDescription, String internalNotes,
                                     Long photographerId, Long editorId) {
        this(customerId, orderType, title, appointmentDate, estimatedDelivery, estimatedAmount, depositAmount,
                deviceBrand, deviceModel, deviceSerial, deviceCondition, problemDescription, internalNotes,
                photographerId, editorId, null, null);
    }
}
