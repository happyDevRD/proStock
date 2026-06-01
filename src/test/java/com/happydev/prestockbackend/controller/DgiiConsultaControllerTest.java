package com.happydev.prestockbackend.controller;

import com.happydev.prestockbackend.dto.DgiiConsultaResultDto;
import com.happydev.prestockbackend.entity.TipoIdentificacion;
import com.happydev.prestockbackend.exception.GlobalExceptionHandler;
import com.happydev.prestockbackend.security.JwtAuthenticationFilter;
import com.happydev.prestockbackend.service.dgii.DgiiConsultaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = DgiiConsultaController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
class DgiiConsultaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DgiiConsultaService dgiiConsultaService;

    @Test
    void consulta_returnsMappedDto() throws Exception {
        DgiiConsultaResultDto dto = new DgiiConsultaResultDto();
        dto.setRncCedula("131996035");
        dto.setTipoIdentificacion(TipoIdentificacion.RNC);
        dto.setNombreRazonSocial("EMPRESA DEMO SRL");
        dto.setSuggestedFirstName("EMPRESA DEMO SRL");
        dto.setSuggestedLastName(".");
        dto.setEstado("ACTIVO");

        given(dgiiConsultaService.consultarPorRncCedula(eq("131996035"))).willReturn(dto);

        mockMvc.perform(get("/api/dgii/consulta").param("rnc", "131996035"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rncCedula", is("131996035")))
                .andExpect(jsonPath("$.tipoIdentificacion", is("RNC")))
                .andExpect(jsonPath("$.estado", is("ACTIVO")));
    }
}
