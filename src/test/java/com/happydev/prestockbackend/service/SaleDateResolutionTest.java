package com.happydev.prestockbackend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.happydev.prestockbackend.dto.SaleDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class SaleDateResolutionTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 14, 22, 42);

    @Test
    void offlineSale_keepsOriginalDate() {
        LocalDateTime original = LocalDateTime.of(2026, 9, 19, 20, 4, 6);

        assertThat(SaleServiceImpl.resolveSaleDate(original, "sale-3f2a-offline", NOW)).isEqualTo(original);
        assertThat(SaleServiceImpl.resolveSaleDate(original, "sale-offline-1789828821924-962002c2", NOW))
                .isEqualTo(original);
    }

    @Test
    void onlineSale_usesServerTime() {
        LocalDateTime clientDate = LocalDateTime.of(2026, 10, 14, 20, 0);

        assertThat(SaleServiceImpl.resolveSaleDate(clientDate, "checkout-9a1b", NOW)).isEqualTo(NOW);
        assertThat(SaleServiceImpl.resolveSaleDate(clientDate, null, NOW)).isEqualTo(NOW);
    }

    @Test
    void offlineSale_withoutDate_usesServerTime() {
        assertThat(SaleServiceImpl.resolveSaleDate(null, "sale-abc", NOW)).isEqualTo(NOW);
    }

    @Test
    void offlineSale_implausibleDate_usesServerTime() {
        assertThat(SaleServiceImpl.resolveSaleDate(NOW.plusHours(2), "sale-abc", NOW)).isEqualTo(NOW);
        assertThat(SaleServiceImpl.resolveSaleDate(NOW.minusDays(32), "sale-abc", NOW)).isEqualTo(NOW);
        // Dentro de la tolerancia de reloj y del límite de antigüedad sí se respeta
        assertThat(SaleServiceImpl.resolveSaleDate(NOW.plusMinutes(5), "sale-abc", NOW)).isEqualTo(NOW.plusMinutes(5));
        assertThat(SaleServiceImpl.resolveSaleDate(NOW.minusDays(30), "sale-abc", NOW)).isEqualTo(NOW.minusDays(30));
    }

    @Test
    void browserIsoDateWithZ_isReadAsUtcLocalDateTime() throws Exception {
        ObjectMapper mapper = Jackson2ObjectMapperBuilder.json().build();

        SaleDto dto = mapper.readValue("{\"saleDate\":\"2026-09-19T20:04:06.123Z\"}", SaleDto.class);

        assertThat(dto.getSaleDate()).isEqualTo(LocalDateTime.of(2026, 9, 19, 20, 4, 6, 123_000_000));
    }
}
