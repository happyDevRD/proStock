package com.happydev.prestockbackend.dto;

public record ServiceOrderStatsDto(
        long active,
        long waitingClient,
        long ready,
        long completedThisMonth
) {}
