package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.dto.SetupWizardStatusDto;
import com.happydev.prestockbackend.entity.CompanyConfig;
import com.happydev.prestockbackend.repository.CategoryRepository;
import com.happydev.prestockbackend.repository.NcfSequenceRepository;
import com.happydev.prestockbackend.repository.ProductRepository;
import com.happydev.prestockbackend.repository.SupplierRepository;
import com.happydev.prestockbackend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class SetupWizardService {

    private static final int CHECKLIST_TOTAL = 7;

    private final CompanyConfigService companyConfigService;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final NcfSequenceRepository ncfSequenceRepository;

    public SetupWizardService(
            CompanyConfigService companyConfigService,
            UserRepository userRepository,
            CategoryRepository categoryRepository,
            SupplierRepository supplierRepository,
            ProductRepository productRepository,
            NcfSequenceRepository ncfSequenceRepository
    ) {
        this.companyConfigService = companyConfigService;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.supplierRepository = supplierRepository;
        this.productRepository = productRepository;
        this.ncfSequenceRepository = ncfSequenceRepository;
    }

    @Transactional(readOnly = true)
    public SetupWizardStatusDto getStatus() {
        Optional<CompanyConfig> companyOpt = companyConfigService.findCompanyConfig();
        boolean empresaStep = companyOpt.map(this::empresaComplete).orElse(false);
        long userCount = userRepository.count();
        long categoryCount = categoryRepository.count();
        long supplierCount = supplierRepository.count();
        long productCount = productRepository.count();

        boolean usuariosStep = userCount >= 2;
        boolean categoriasStep = categoryCount >= 1;
        boolean suplidoresStep = supplierCount >= 1;
        boolean productosStep = productCount >= 1;
        boolean ncf32 = ncfSequenceRepository.findByTipoComprobante("32").isPresent();
        boolean ncf31 = ncfSequenceRepository.findByTipoComprobante("31").isPresent();

        int completed = 0;
        if (empresaStep) {
            completed++;
        }
        if (usuariosStep) {
            completed++;
        }
        if (categoriasStep) {
            completed++;
        }
        if (suplidoresStep) {
            completed++;
        }
        if (productosStep) {
            completed++;
        }
        if (ncf32) {
            completed++;
        }
        if (ncf31) {
            completed++;
        }

        return new SetupWizardStatusDto(
                empresaStep,
                usuariosStep,
                categoriasStep,
                suplidoresStep,
                productosStep,
                ncf32,
                ncf31,
                userCount,
                categoryCount,
                supplierCount,
                productCount,
                completed,
                CHECKLIST_TOTAL
        );
    }

    private boolean empresaComplete(CompanyConfig c) {
        if (c.getRnc() == null || c.getRnc().isBlank()
                || c.getRazonSocial() == null || c.getRazonSocial().isBlank()
                || c.getDireccion() == null || c.getDireccion().isBlank()) {
            return false;
        }
        if (c.getMunicipioCodigo() == null || c.getMunicipioCodigo().length() != 6
                || c.getProvinciaCodigo() == null || c.getProvinciaCodigo().length() != 6) {
            return false;
        }
        return c.getActividadEconomica() != null && !c.getActividadEconomica().isBlank()
                && c.getNumeroTelefono() != null && !c.getNumeroTelefono().isBlank()
                && c.getCorreoElectronico() != null && !c.getCorreoElectronico().isBlank();
    }
}
