package com.happydev.prestockbackend.dto;

import com.happydev.prestockbackend.entity.TipoIdentificacion;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DgiiConsultaResultDto {

    private String rncCedula;

    private TipoIdentificacion tipoIdentificacion;

    private String nombreRazonSocial;

    private String nombreComercial;

    private String estado;

    private String regimenPagos;

    private String actividadEconomica;

    private String facturadorElectronico;

    /** Nombre sugerido para el formulario de cliente (nombre o razón social). */
    private String suggestedFirstName;

    /** Apellido sugerido; para personas jurídicas suele ser "." */
    private String suggestedLastName;
}
