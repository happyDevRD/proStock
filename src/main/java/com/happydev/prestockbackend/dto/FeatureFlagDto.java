package com.happydev.prestockbackend.dto;

import java.util.List;

public record FeatureFlagDto(
        String code,
        String category,
        String name,
        String description,
        boolean enabled,
        List<String> dependsOn,
        boolean dependenciesSatisfied
) {}
