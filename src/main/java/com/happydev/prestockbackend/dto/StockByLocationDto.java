package com.happydev.prestockbackend.dto;

public record StockByLocationDto(
        Long locationId,
        String locationName,
        String locationCode,
        int quantity
) {}
