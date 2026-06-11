package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.exception.FeatureDisabledException;
import com.happydev.prestockbackend.feature.FeatureCatalog;
import org.springframework.stereotype.Component;

@Component
public class ServiceOrderAccessGuard {

    public static final String FEATURE_CODE = "module.service_orders";

    private final FeatureConfigService featureConfigService;

    public ServiceOrderAccessGuard(FeatureConfigService featureConfigService) {
        this.featureConfigService = featureConfigService;
    }

    public void requireEnabled() {
        if (!featureConfigService.isEnabled(FEATURE_CODE)) {
            throw new FeatureDisabledException(FEATURE_CODE);
        }
    }
}
