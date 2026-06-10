package com.happydev.prestockbackend.controller;

import com.happydev.prestockbackend.dto.FeatureFlagDto;
import com.happydev.prestockbackend.service.FeatureConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/features")
public class FeatureConfigController {

    private final FeatureConfigService featureConfigService;

    public FeatureConfigController(FeatureConfigService featureConfigService) {
        this.featureConfigService = featureConfigService;
    }

    @GetMapping
    public ResponseEntity<List<FeatureFlagDto>> getFeatures() {
        return ResponseEntity.ok(featureConfigService.getAll());
    }

    @PutMapping
    public ResponseEntity<List<FeatureFlagDto>> updateFeatures(@RequestBody List<FeatureFlagDto> updates,
                                                                 Principal principal) {
        String actor = principal != null ? principal.getName() : null;
        return ResponseEntity.ok(featureConfigService.updateAll(updates, actor));
    }
}
