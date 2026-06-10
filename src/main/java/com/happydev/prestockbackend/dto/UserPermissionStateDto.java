package com.happydev.prestockbackend.dto;

public record UserPermissionStateDto(
        String code,
        String category,
        String name,
        String description,
        boolean fromRole,
        Boolean override,
        boolean effective
) {}
