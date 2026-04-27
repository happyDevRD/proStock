package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.entity.CompanyConfig;
import org.springframework.lang.NonNull;

import java.util.Optional;

public interface CompanyConfigService {
    Optional<CompanyConfig> findCompanyConfig();
    CompanyConfig saveOrUpdate(@NonNull CompanyConfig companyConfig);
}
