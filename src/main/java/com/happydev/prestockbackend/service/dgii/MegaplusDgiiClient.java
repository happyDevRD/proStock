package com.happydev.prestockbackend.service.dgii;

import com.happydev.prestockbackend.config.DgiiMegaplusProperties;
import com.happydev.prestockbackend.exception.DgiiConsultaException;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class MegaplusDgiiClient {

    private final DgiiMegaplusProperties properties;
    private final RestClient restClient;

    public MegaplusDgiiClient(DgiiMegaplusProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeoutMs());
        factory.setReadTimeout(properties.getReadTimeoutMs());
        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    public MegaplusNombresResponse consultarPorNombre(String buscar) {
        if (!properties.isEnabled()) {
            throw new DgiiConsultaException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "DGII_DISABLED",
                    "La consulta DGII no está habilitada en este servidor."
            );
        }
        String baseUrl = properties.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new DgiiConsultaException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "DGII_MISCONFIGURED",
                    "Falta configurar la URL del proveedor DGII."
            );
        }
        String url = UriComponentsBuilder.fromUriString(baseUrl.replaceAll("/$", ""))
                .path("/api/consulta/nombres")
                .queryParam("buscar", buscar.trim())
                .toUriString();
        try {
            return restClient.get()
                    .uri(url)
                    .exchange((request, response) -> {
                        MegaplusNombresResponse parsed = response.bodyTo(MegaplusNombresResponse.class);
                        if (parsed == null) {
                            throw new DgiiConsultaException(
                                    HttpStatus.BAD_GATEWAY,
                                    "DGII_EMPTY_RESPONSE",
                                    "El servicio DGII no devolvió datos."
                            );
                        }
                        return parsed;
                    });
        } catch (DgiiConsultaException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw new DgiiConsultaException(
                    HttpStatus.BAD_GATEWAY,
                    "DGII_UNAVAILABLE",
                    "No se pudo contactar el servicio de consulta DGII. Intenta más tarde."
            );
        }
    }

    public MegaplusConsultaResponse consultarPorRncCedula(String rncDigits) {
        if (!properties.isEnabled()) {
            throw new DgiiConsultaException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "DGII_DISABLED",
                    "La consulta DGII no está habilitada en este servidor."
            );
        }

        String baseUrl = properties.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new DgiiConsultaException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "DGII_MISCONFIGURED",
                    "Falta configurar la URL del proveedor DGII."
            );
        }

        String url = UriComponentsBuilder.fromUriString(baseUrl.replaceAll("/$", ""))
                .path("/api/consulta")
                .queryParam("rnc", rncDigits)
                .toUriString();

        try {
            MegaplusConsultaResponse body = restClient.get()
                    .uri(url)
                    .exchange((request, response) -> {
                        MegaplusConsultaResponse parsed = response.bodyTo(MegaplusConsultaResponse.class);
                        if (parsed == null) {
                            throw new DgiiConsultaException(
                                    HttpStatus.BAD_GATEWAY,
                                    "DGII_EMPTY_RESPONSE",
                                    "El servicio DGII no devolvió datos."
                            );
                        }
                        return parsed;
                    });
            return body;
        } catch (DgiiConsultaException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw new DgiiConsultaException(
                    HttpStatus.BAD_GATEWAY,
                    "DGII_UNAVAILABLE",
                    "No se pudo contactar el servicio de consulta DGII. Intenta más tarde."
            );
        }
    }
}
