package com.happydev.prestockbackend.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DgiiNombreHitDto {

    private String rncCedula;

    private String nombreRazonSocial;

    private String nombreComercial;

    private String estado;

    private String regimenPagos;

    private String facturadorElectronico;
}
