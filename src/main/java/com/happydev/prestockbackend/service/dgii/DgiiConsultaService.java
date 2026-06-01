package com.happydev.prestockbackend.service.dgii;

import com.happydev.prestockbackend.dto.DgiiConsultaResultDto;
import com.happydev.prestockbackend.dto.DgiiNombreHitDto;
import com.happydev.prestockbackend.dto.DgiiNombresSearchDto;
import com.happydev.prestockbackend.entity.TipoIdentificacion;
import com.happydev.prestockbackend.exception.DgiiConsultaException;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class DgiiConsultaService {

    private static final String[] LEGAL_SUFFIXES = {
            "S.A.S.", "S. A. S.", "S.A.", "SRL", "EIRL", "SAS", "CXA", "C POR A", "S EN C", "S EN C."
    };

    private final MegaplusDgiiClient megaplusDgiiClient;

    public DgiiConsultaService(MegaplusDgiiClient megaplusDgiiClient) {
        this.megaplusDgiiClient = megaplusDgiiClient;
    }

    public DgiiConsultaResultDto consultarPorRncCedula(@NonNull String rawRncCedula) {
        String digits = normalizeDigits(rawRncCedula);
        if (digits.length() != 9 && digits.length() != 11) {
            throw new DgiiConsultaException(
                    HttpStatus.BAD_REQUEST,
                    "DGII_INVALID_RNC",
                    "El RNC o la cédula debe tener 9 u 11 dígitos."
            );
        }

        MegaplusConsultaResponse response = megaplusDgiiClient.consultarPorRncCedula(digits);
        if (response.error()) {
            throw mapProviderError(response);
        }

        DgiiConsultaResultDto dto = new DgiiConsultaResultDto();
        String rncCedula = firstNonBlank(response.rncConsultado(), digits);
        dto.setRncCedula(rncCedula);
        dto.setTipoIdentificacion(rncCedula.length() == 11 ? TipoIdentificacion.CEDULA : TipoIdentificacion.RNC);
        dto.setNombreRazonSocial(trimToNull(response.nombreRazonSocial()));
        dto.setNombreComercial(trimToNull(response.nombreComercial()));
        dto.setEstado(trimToNull(response.estado()));
        dto.setRegimenPagos(trimToNull(response.regimenDePagos()));
        dto.setActividadEconomica(trimToNull(response.actividadEconomica()));
        dto.setFacturadorElectronico(trimToNull(response.facturadorElectronico()));
        applySuggestedNames(dto);
        return dto;
    }

    public DgiiNombresSearchDto consultarPorNombre(@NonNull String buscar) {
        String term = buscar.trim();
        if (term.length() < 3) {
            throw new DgiiConsultaException(
                    HttpStatus.BAD_REQUEST,
                    "DGII_INVALID_SEARCH",
                    "Escribe al menos 3 caracteres para buscar por nombre."
            );
        }

        MegaplusNombresResponse response = megaplusDgiiClient.consultarPorNombre(term);
        if (response.error()) {
            throw mapProviderError(
                    response.codigoHttp(),
                    response.mensaje(),
                    "No se pudo buscar por nombre en la DGII."
            );
        }

        DgiiNombresSearchDto dto = new DgiiNombresSearchDto();
        if (response.infoPaginacion() != null) {
            dto.setPaginaActual(response.infoPaginacion().paginaActual());
            dto.setPaginasTotales(response.infoPaginacion().paginasTotales());
            dto.setTotalEnEstaPagina(response.infoPaginacion().totalEnEstaPagina());
        }
        List<MegaplusNombreHit> hits = response.resultados() != null ? response.resultados() : List.of();
        List<DgiiNombreHitDto> mapped = new ArrayList<>();
        for (MegaplusNombreHit hit : hits) {
            DgiiNombreHitDto row = new DgiiNombreHitDto();
            row.setRncCedula(normalizeDigits(hit.cedulaRnc()));
            row.setNombreRazonSocial(trimToNull(hit.nombreRazonSocial()));
            row.setNombreComercial(trimToNull(hit.nombreComercial()));
            row.setEstado(trimToNull(hit.estado()));
            row.setRegimenPagos(trimToNull(hit.regimenDePagos()));
            row.setFacturadorElectronico(trimToNull(hit.facturadorElectronico()));
            if (row.getRncCedula() != null && !row.getRncCedula().isBlank()) {
                mapped.add(row);
            }
        }
        dto.setResultados(mapped);
        return dto;
    }

    static void applySuggestedNames(DgiiConsultaResultDto dto) {
        String rnc = dto.getRncCedula();
        if (rnc != null && rnc.length() == 11) {
            String displayName = firstNonBlank(dto.getNombreRazonSocial(), dto.getNombreComercial());
            if (displayName == null) {
                dto.setSuggestedFirstName("");
                dto.setSuggestedLastName("");
                return;
            }
            displayName = displayName.trim();
            int lastSpace = displayName.lastIndexOf(' ');
            if (lastSpace > 0 && lastSpace < displayName.length() - 1) {
                dto.setSuggestedFirstName(displayName.substring(0, lastSpace).trim());
                dto.setSuggestedLastName(displayName.substring(lastSpace + 1).trim());
            } else {
                dto.setSuggestedFirstName(displayName);
                dto.setSuggestedLastName("NA");
            }
            return;
        }

        String razon = firstNonBlank(dto.getNombreRazonSocial(), dto.getNombreComercial());
        if (razon == null) {
            dto.setSuggestedFirstName("");
            dto.setSuggestedLastName("");
            return;
        }
        razon = razon.trim();
        String upper = razon.toUpperCase();
        for (String suffix : LEGAL_SUFFIXES) {
            String suffixUpper = suffix.toUpperCase();
            if (upper.endsWith(suffixUpper)) {
                int idx = upper.lastIndexOf(suffixUpper);
                if (idx > 0) {
                    String first = razon.substring(0, idx).trim();
                    String last = razon.substring(idx).trim();
                    if (first.length() >= 2 && last.length() >= 2) {
                        dto.setSuggestedFirstName(first);
                        dto.setSuggestedLastName(last);
                        return;
                    }
                }
            }
        }
        dto.setSuggestedFirstName(razon);
        dto.setSuggestedLastName("DG");
    }

    private static DgiiConsultaException mapProviderError(MegaplusConsultaResponse response) {
        return mapProviderError(response.codigoHttp(), response.mensaje(), "No se pudo consultar el RNC o la cédula en la DGII.");
    }

    private static DgiiConsultaException mapProviderError(Integer codigoHttp, String mensaje, String fallback) {
        int code = codigoHttp != null ? codigoHttp : 502;
        String message = mensaje != null && !mensaje.isBlank() ? mensaje : fallback;
        HttpStatus status = switch (code) {
            case 400 -> HttpStatus.BAD_REQUEST;
            case 404 -> HttpStatus.NOT_FOUND;
            default -> HttpStatus.BAD_GATEWAY;
        };
        String errorCode = switch (code) {
            case 400 -> "DGII_BAD_REQUEST";
            case 404 -> "DGII_NOT_FOUND";
            default -> "DGII_PROVIDER_ERROR";
        };
        return new DgiiConsultaException(status, errorCode, message);
    }

    private static String normalizeDigits(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replaceAll("\\D", "");
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
