package com.happydev.prestockbackend.controller;

import com.happydev.prestockbackend.dto.SaleDto;
import com.happydev.prestockbackend.entity.SaleStatus;
import com.happydev.prestockbackend.service.SaleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaleControllerExportTest {

    private SaleService saleService;
    private SaleController saleController;

    @BeforeEach
    void setup() {
        saleService = Mockito.mock(SaleService.class);
        saleController = new SaleController(saleService);
    }

    @Test
    void exportSales_shouldIncludeHeadersAndRows() {
        SaleDto sale = new SaleDto();
        sale.setId(10L);
        sale.setSaleDate(LocalDateTime.of(2026, 4, 27, 10, 30));
        sale.setStatus(SaleStatus.COMPLETED);
        sale.setTipoComprobante("31");
        sale.setNcf("E310000000001");
        sale.setMontoTotal(new BigDecimal("1500.00"));
        sale.setTotalItbis(new BigDecimal("228.81"));

        Mockito.when(saleService.findAllSales()).thenReturn(List.of(sale));

        ResponseEntity<String> response = saleController.exportSales(null, null, null);
        String body = Objects.requireNonNull(response.getBody(), "CSV response body should not be null");

        assertEquals(200, response.getStatusCode().value());
        assertTrue(body.contains("id,saleDate,status,tipoComprobante,ncf,montoTotal,totalItbis"));
        assertTrue(body.contains("10,2026-04-27T10:30,COMPLETED,31,E310000000001,1500.00,228.81"));
    }

    @Test
    void exportSales_shouldFilterByStatus() {
        SaleDto completed = new SaleDto();
        completed.setId(1L);
        completed.setSaleDate(LocalDateTime.of(2026, 4, 27, 8, 0));
        completed.setStatus(SaleStatus.COMPLETED);

        SaleDto pending = new SaleDto();
        pending.setId(2L);
        pending.setSaleDate(LocalDateTime.of(2026, 4, 27, 9, 0));
        pending.setStatus(SaleStatus.PENDING);

        Mockito.when(saleService.findAllSales()).thenReturn(List.of(completed, pending));

        ResponseEntity<String> response = saleController.exportSales(null, null, SaleStatus.PENDING);
        String body = Objects.requireNonNull(response.getBody(), "CSV response body should not be null");

        assertTrue(body.contains("2,2026-04-27T09:00"));
        assertTrue(!body.contains("1,2026-04-27T08:00"));
    }
}
