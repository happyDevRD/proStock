package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.entity.CompanyConfig;
import com.happydev.prestockbackend.repository.CompanyConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyConfigServiceImplTest {

    @Mock
    private CompanyConfigRepository companyConfigRepository;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private CompanyConfigServiceImpl companyConfigService;

    private CompanyConfig incoming;

    @BeforeEach
    void setUp() {
        incoming = new CompanyConfig();
        incoming.setId(0L);
        incoming.setRnc("101234567");
        incoming.setRazonSocial("ProStock SRL");
        incoming.setDireccion("Av. Principal #123");
        incoming.setMunicipioCodigo("010100");
        incoming.setProvinciaCodigo("010100");
        incoming.setActividadEconomica("Comercio");
        incoming.setNumeroTelefono("8095551234");
        incoming.setCorreoElectronico("facturacion@prostock.do");
    }

    @Test
    void saveOrUpdate_whenNoExistingRow_clearsZeroIdBeforeInsert() {
        given(companyConfigRepository.findFirstByOrderByIdAsc()).willReturn(Optional.empty());
        when(companyConfigRepository.save(any(CompanyConfig.class))).thenAnswer(invocation -> {
            CompanyConfig toSave = invocation.getArgument(0);
            assertNull(toSave.getId());
            toSave.setId(1L);
            return toSave;
        });

        companyConfigService.saveOrUpdate(incoming);
    }
}
