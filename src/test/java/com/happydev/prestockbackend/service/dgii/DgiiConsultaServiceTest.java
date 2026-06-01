package com.happydev.prestockbackend.service.dgii;

import com.happydev.prestockbackend.dto.DgiiConsultaResultDto;
import com.happydev.prestockbackend.entity.TipoIdentificacion;
import com.happydev.prestockbackend.exception.DgiiConsultaException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class DgiiConsultaServiceTest {

    @Mock
    private MegaplusDgiiClient megaplusDgiiClient;

    @InjectMocks
    private DgiiConsultaService dgiiConsultaService;

    @Test
    void consultarPorRncCedula_mapsBusinessContributor() {
        given(megaplusDgiiClient.consultarPorRncCedula(eq("131996035")))
                .willReturn(new MegaplusConsultaResponse(
                        false,
                        200,
                        "Consulta Exitosa",
                        "131-99603-5",
                        "AGROPECUARIA DELIA & MILO AGRODEMI SRL",
                        "AGROPECUARIA DELIA & MILO AGRODEMI",
                        "",
                        "NORMAL",
                        "ACTIVO",
                        "CRÍA DE ANIMALES",
                        "ADM LOCAL LA VEGA",
                        "SI",
                        "131996035"
                ));

        DgiiConsultaResultDto result = dgiiConsultaService.consultarPorRncCedula("131-99603-5");

        assertThat(result.getRncCedula()).isEqualTo("131996035");
        assertThat(result.getTipoIdentificacion()).isEqualTo(TipoIdentificacion.RNC);
        assertThat(result.getSuggestedFirstName()).isEqualTo("AGROPECUARIA DELIA & MILO AGRODEMI");
        assertThat(result.getSuggestedLastName()).isEqualTo("SRL");
        assertThat(result.getEstado()).isEqualTo("ACTIVO");
    }

    @Test
    void consultarPorRncCedula_rejectsInvalidLength() {
        assertThatThrownBy(() -> dgiiConsultaService.consultarPorRncCedula("123"))
                .isInstanceOf(DgiiConsultaException.class)
                .satisfies(ex -> {
                    DgiiConsultaException dgii = (DgiiConsultaException) ex;
                    assertThat(dgii.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(dgii.getCode()).isEqualTo("DGII_INVALID_RNC");
                });
    }

    @Test
    void consultarPorRncCedula_propagatesProviderNotFound() {
        given(megaplusDgiiClient.consultarPorRncCedula(eq("00100000001")))
                .willReturn(new MegaplusConsultaResponse(
                        true,
                        404,
                        "el rnc/cedula consultado no se encuentra inscrito como contribuyente.",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "00100000001"
                ));

        assertThatThrownBy(() -> dgiiConsultaService.consultarPorRncCedula("00100000001"))
                .isInstanceOf(DgiiConsultaException.class)
                .satisfies(ex -> {
                    DgiiConsultaException dgii = (DgiiConsultaException) ex;
                    assertThat(dgii.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(dgii.getCode()).isEqualTo("DGII_NOT_FOUND");
                });
    }

    @Test
    void applySuggestedNames_splitsPersonName() {
        DgiiConsultaResultDto dto = new DgiiConsultaResultDto();
        dto.setRncCedula("00112345678");
        dto.setNombreRazonSocial("JUAN CARLOS PEREZ LOPEZ");
        DgiiConsultaService.applySuggestedNames(dto);
        assertThat(dto.getSuggestedFirstName()).isEqualTo("JUAN CARLOS PEREZ");
        assertThat(dto.getSuggestedLastName()).isEqualTo("LOPEZ");
    }
}
