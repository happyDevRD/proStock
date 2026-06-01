package com.happydev.prestockbackend.service.dgii;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MegaplusConsultaResponse(
        boolean error,
        @JsonProperty("codigo_http") Integer codigoHttp,
        String mensaje,
        @JsonProperty("cedula_rnc") String cedulaRnc,
        @JsonProperty("nombre_razon_social") String nombreRazonSocial,
        @JsonProperty("nombre_comercial") String nombreComercial,
        String categoria,
        @JsonProperty("regimen_de_pagos") String regimenDePagos,
        String estado,
        @JsonProperty("actividad_economica") String actividadEconomica,
        @JsonProperty("administracion_local") String administracionLocal,
        @JsonProperty("facturador_electronico") String facturadorElectronico,
        @JsonProperty("rnc_consultado") String rncConsultado
) {
}
