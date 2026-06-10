package com.happydev.prestockbackend.dto;

public record PermissionDto(
        String code,
        String category,
        String name,
        String description
) {}
