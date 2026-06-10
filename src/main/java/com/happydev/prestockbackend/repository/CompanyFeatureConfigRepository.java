package com.happydev.prestockbackend.repository;

import com.happydev.prestockbackend.entity.CompanyFeatureConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompanyFeatureConfigRepository extends JpaRepository<CompanyFeatureConfig, Long> {
    Optional<CompanyFeatureConfig> findByFeatureCode(String featureCode);
}
