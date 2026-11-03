package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.dto.Dgii607ReportDto;
import com.happydev.prestockbackend.dto.Dgii607RowDto;
import com.happydev.prestockbackend.entity.CreditNote;
import com.happydev.prestockbackend.entity.Sale;
import com.happydev.prestockbackend.entity.SaleStatus;
import com.happydev.prestockbackend.repository.CreditNoteRepository;
import com.happydev.prestockbackend.repository.SaleRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Nota de crédito por el total: la venta queda con montos vigentes en 0 (saldo/ingresos netos),
 * pero el 607 debe llevar la factura con sus montos originales y la nota aparte con NCF modificado.
 */
@SpringBootTest
@Transactional
class CreditNote607IntegrationTest {

    private static final YearMonth PERIODO = YearMonth.of(2031, 3);

    @Autowired private SaleRepository saleRepository;
    @Autowired private CreditNoteRepository creditNoteRepository;
    @Autowired private DgiiReportService dgiiReportService;
    @Autowired private EntityManager entityManager;

    @Test
    void notaDeCreditoTotal_noDejaLaFacturaEnCeroEnEl607() {
        // Así queda una venta tras una nota de crédito por el total (createCreditNote recalcula lo vigente)
        Sale sale = new Sale();
        sale.setSaleDate(LocalDateTime.of(2031, 3, 5, 10, 0));
        sale.setStatus(SaleStatus.COMPLETED);
        sale.setTipoComprobante("01");
        sale.setNcf("B0199990001");
        sale.setMontoGravadoTotal(new BigDecimal("0.00"));
        sale.setMontoExento(new BigDecimal("0.00"));
        sale.setTotalItbis(new BigDecimal("0.00"));
        sale.setMontoTotal(new BigDecimal("0.00"));
        sale.setPaidAmount(new BigDecimal("0.00"));
        sale = saleRepository.save(sale);

        CreditNote cn = new CreditNote();
        cn.setSale(sale);
        cn.setNcf("B0499990001");
        cn.setNcfModificado("B0199990001");
        cn.setCreatedAt(LocalDateTime.of(2031, 3, 20, 9, 0));
        cn.setMontoGravadoTotal(new BigDecimal("1000.00"));
        cn.setMontoExento(new BigDecimal("200.00"));
        cn.setTotalItbis(new BigDecimal("180.00"));
        cn.setMontoTotal(new BigDecimal("1380.00"));
        creditNoteRepository.save(cn);

        entityManager.flush();
        entityManager.clear();

        // La venta expone lo acreditado: lo facturado = montoTotal + creditedAmount
        Sale reloaded = saleRepository.findById(sale.getId()).orElseThrow();
        assertThat(reloaded.getCreditedAmount()).isEqualByComparingTo("1380.00");

        Dgii607ReportDto report = dgiiReportService.build607(PERIODO);
        Dgii607RowDto factura = report.getRows().stream()
                .filter(r -> "B0199990001".equals(r.getNcf())).findFirst().orElseThrow();
        Dgii607RowDto nota = report.getRows().stream()
                .filter(r -> "B0499990001".equals(r.getNcf())).findFirst().orElseThrow();

        assertThat(factura.getMontoFacturado()).isEqualByComparingTo("1200.00");
        assertThat(factura.getItbisFacturado()).isEqualByComparingTo("180.00");
        assertThat(factura.getNcfModificado()).isEmpty();

        assertThat(nota.getNcfModificado()).isEqualTo("B0199990001");
        assertThat(nota.getMontoFacturado()).isEqualByComparingTo("1200.00");
        assertThat(nota.getItbisFacturado()).isEqualByComparingTo("180.00");
    }
}
