package com.happydev.prestockbackend.dto;

import com.happydev.prestockbackend.entity.LocationType;

import java.time.LocalDateTime;

public record LocationDto(
        Long id,
        String name,
        String code,
        LocationType type,
        String address,
        String phone,
        boolean active,
        boolean isDefault,
        LocalDateTime createdAt
) {}
