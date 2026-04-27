package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.entity.CompanyConfig;
import com.happydev.prestockbackend.repository.CompanyConfigRepository;
import jakarta.transaction.Transactional;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@Transactional
public class CompanyConfigServiceImpl implements CompanyConfigService {

    private final CompanyConfigRepository companyConfigRepository;

    public CompanyConfigServiceImpl(CompanyConfigRepository companyConfigRepository) {
        this.companyConfigRepository = companyConfigRepository;
    }

    @Override
    public Optional<CompanyConfig> findCompanyConfig() {
        return companyConfigRepository.findFirstByOrderByIdAsc();
    }

    @Override
    public CompanyConfig saveOrUpdate(@NonNull CompanyConfig companyConfig) {
        return companyConfigRepository.findFirstByOrderByIdAsc()
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
                    return companyConfigRepository.save(existingConfig);
                })
                .orElseGet(() -> companyConfigRepository.save(companyConfig));
    }
}
