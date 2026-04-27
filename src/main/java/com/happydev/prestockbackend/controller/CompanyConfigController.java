package com.happydev.prestockbackend.controller;

import com.happydev.prestockbackend.entity.CompanyConfig;
import com.happydev.prestockbackend.service.CompanyConfigService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@RestController
@RequestMapping("/api/company-config")
public class CompanyConfigController {

    private final CompanyConfigService companyConfigService;

    public CompanyConfigController(CompanyConfigService companyConfigService) {
        this.companyConfigService = companyConfigService;
    }

    @GetMapping
    public ResponseEntity<CompanyConfig> getCompanyConfig() {
        return companyConfigService.findCompanyConfig()
                .map(companyConfig -> new ResponseEntity<>(companyConfig, HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @PutMapping
    public ResponseEntity<CompanyConfig> saveOrUpdateCompanyConfig(@Valid @RequestBody CompanyConfig companyConfig) {
        CompanyConfig savedConfig = companyConfigService.saveOrUpdate(
                Objects.requireNonNull(companyConfig, "companyConfig no puede ser null")
        );
        return new ResponseEntity<>(savedConfig, HttpStatus.OK);
    }
}
