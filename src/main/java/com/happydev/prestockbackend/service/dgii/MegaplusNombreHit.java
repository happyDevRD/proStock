package com.happydev.prestockbackend.service.dgii;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MegaplusNombreHit(
        @JsonProperty("cedula_rnc") String cedulaRnc,
        @JsonProperty("nombre_razon_social") String nombreRazonSocial,
        @JsonProperty("nombre_comercial") String nombreComercial,
        String estado,
        @JsonProperty("regimen_de_pagos") String regimenDePagos,
        @JsonProperty("facturador_electronico") String facturadorElectronico
) {
}
