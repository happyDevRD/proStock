package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.dto.FeatureFlagDto;

import java.util.List;

public interface FeatureConfigService {
    List<FeatureFlagDto> getAll();

    List<FeatureFlagDto> updateAll(List<FeatureFlagDto> updates, String actorUsername);
}
