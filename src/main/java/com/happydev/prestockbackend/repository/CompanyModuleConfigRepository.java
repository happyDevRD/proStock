package com.happydev.prestockbackend.repository;

import com.happydev.prestockbackend.entity.AppModule;
import com.happydev.prestockbackend.entity.CompanyModuleConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompanyModuleConfigRepository extends JpaRepository<CompanyModuleConfig, Long> {
    Optional<CompanyModuleConfig> findByModule(AppModule module);
}
