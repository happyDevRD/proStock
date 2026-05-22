package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.entity.CompanyConfig;
import com.happydev.prestockbackend.repository.CompanyConfigRepository;
import com.happydev.prestockbackend.util.SecurityAuditUtils;
import jakarta.transaction.Transactional;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Service
@Transactional
public class CompanyConfigServiceImpl implements CompanyConfigService {

    private final CompanyConfigRepository companyConfigRepository;

    private final AuditService auditService;

    public CompanyConfigServiceImpl(CompanyConfigRepository companyConfigRepository, AuditService auditService) {
        this.companyConfigRepository = companyConfigRepository;
        this.auditService = auditService;
    }

    @Override
    public Optional<CompanyConfig> findCompanyConfig() {
        return companyConfigRepository.findFirstByOrderByIdAsc();
    }

    @Override
    public CompanyConfig saveOrUpdate(@NonNull CompanyConfig companyConfig) {
        CompanyConfig saved = companyConfigRepository.findFirstByOrderByIdAsc()
                .map(existingConfig -> {
                    existingConfig.setRnc(companyConfig.getRnc());
                    existingConfig.setRazonSocial(companyConfig.getRazonSocial());
                    existingConfig.setNombreComercial(companyConfig.getNombreComercial());
                    existingConfig.setDireccion(companyConfig.getDireccion());
                    existingConfig.setMunicipioCodigo(companyConfig.getMunicipioCodigo());
                    existingConfig.setProvinciaCodigo(companyConfig.getProvinciaCodigo());
                    existingConfig.setActividadEconomica(companyConfig.getActividadEconomica());
                    existingConfig.setNumeroTelefono(companyConfig.getNumeroTelefono());
                    existingConfig.setCorreoElectronico(companyConfig.getCorreoElectronico());
                    existingConfig.setLogoFileName(companyConfig.getLogoFileName());
                    existingConfig.setInvoiceHeaderNote(companyConfig.getInvoiceHeaderNote());
                    existingConfig.setInvoiceFooterText(companyConfig.getInvoiceFooterText());
                    return companyConfigRepository.save(existingConfig);
                })
                .orElseGet(() -> {
                    // id=0 desde el cliente se interpreta como entidad existente (merge) y rompe el INSERT.
                    companyConfig.setId(null);
                    return companyConfigRepository.save(companyConfig);
                });
        auditService.record(
                SecurityAuditUtils.currentUsernameOrNull(),
                "COMPANY_CONFIG_SAVED",
                "CompanyConfig",
                saved.getId(),
                Map.of(
                        "rnc", saved.getRnc() != null ? saved.getRnc() : "",
                        "razonSocial", saved.getRazonSocial() != null ? saved.getRazonSocial() : ""
                )
        );
        return saved;
    }
}
