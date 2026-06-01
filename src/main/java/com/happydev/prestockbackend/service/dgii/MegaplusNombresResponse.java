package com.happydev.prestockbackend.service.dgii;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MegaplusNombresResponse(
        boolean error,
        @JsonProperty("codigo_http") Integer codigoHttp,
        String mensaje,
        @JsonProperty("info_paginacion") MegaplusPaginacion infoPaginacion,
        List<MegaplusNombreHit> resultados
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MegaplusPaginacion(
            @JsonProperty("pagina_actual") Integer paginaActual,
            @JsonProperty("paginas_totales") Integer paginasTotales,
            @JsonProperty("total_en_esta_pagina") Integer totalEnEstaPagina
    ) {
    }
}
