package com.happydev.prestockbackend.dto;

import com.happydev.prestockbackend.entity.ServiceOrderStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AdvanceStageRequest(
        @NotBlank String stage,
        @NotNull ServiceOrderStatus status,
        String notes
) {}
