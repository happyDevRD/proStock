package com.happydev.prestockbackend.dto;

import java.time.LocalDateTime;

public record ServiceOrderStageDto(
        Long id,
        String stageName,
        LocalDateTime enteredAt,
        String notes,
        String actor
) {}
