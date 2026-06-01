package com.happydev.prestockbackend.dto;

public record AccountDto(
        Long id,
        String code,
        String name,
        String type,
        String nature,
        Long parentId,
        boolean allowsTransactions,
        boolean isSystem,
        boolean active
) {}
