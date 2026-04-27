package com.happydev.prestockbackend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.happydev.prestockbackend.entity.CompanyConfig;
import com.happydev.prestockbackend.service.CompanyConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CompanyConfigController.class)
@SuppressWarnings("null")
class CompanyConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CompanyConfigService companyConfigService;

    @Autowired
    private ObjectMapper objectMapper;

    private CompanyConfig companyConfig;

    @BeforeEach
    void setUp() {
        companyConfig = new CompanyConfig();
        companyConfig.setId(1L);
        companyConfig.setRnc("101234567");
        companyConfig.setRazonSocial("ProStock SRL");
        companyConfig.setNombreComercial("ProStock");
        companyConfig.setDireccion("Av. Principal #123");
        companyConfig.setMunicipioCodigo("010100");
        companyConfig.setProvinciaCodigo("010100");
        companyConfig.setActividadEconomica("Comercio al por mayor");
        companyConfig.setNumeroTelefono("8095551234");
        companyConfig.setCorreoElectronico("facturacion@prostock.do");
    }

    @Test
    void getCompanyConfig_WhenExists_ReturnsCompanyConfig() throws Exception {
        given(companyConfigService.findCompanyConfig()).willReturn(Optional.of(companyConfig));

        mockMvc.perform(get("/api/company-config")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rnc", is(companyConfig.getRnc())))
                .andExpect(jsonPath("$.razonSocial", is(companyConfig.getRazonSocial())));
    }

    @Test
    void getCompanyConfig_WhenNotExists_ReturnsNotFound() throws Exception {
        given(companyConfigService.findCompanyConfig()).willReturn(Optional.empty());

        mockMvc.perform(get("/api/company-config")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    void saveOrUpdateCompanyConfig_ValidData_ReturnsOk() throws Exception {
        given(companyConfigService.saveOrUpdate(any(CompanyConfig.class))).willReturn(companyConfig);

        mockMvc.perform(put("/api/company-config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(companyConfig)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correoElectronico", is(companyConfig.getCorreoElectronico())));
    }

    @Test
    void saveOrUpdateCompanyConfig_InvalidRnc_ReturnsBadRequest() throws Exception {
        companyConfig.setRnc("12345");

        mockMvc.perform(put("/api/company-config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(companyConfig)))
                .andExpect(status().isBadRequest());
    }
}
