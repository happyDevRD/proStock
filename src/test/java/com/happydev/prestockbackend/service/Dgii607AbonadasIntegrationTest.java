package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.dto.Dgii607ReportDto;
import com.happydev.prestockbackend.dto.Dgii607RowDto;
import com.happydev.prestockbackend.dto.Dgii608ReportDto;
import com.happydev.prestockbackend.entity.CreditNote;
import com.happydev.prestockbackend.entity.PaymentMethod;
import com.happydev.prestockbackend.entity.Sale;
import com.happydev.prestockbackend.entity.SalePayment;
import com.happydev.prestockbackend.entity.SaleStatus;
import com.happydev.prestockbackend.repository.CreditNoteRepository;
import com.happydev.prestockbackend.repository.SalePaymentRepository;
import com.happydev.prestockbackend.repository.SaleRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El 607 lleva todo comprobante con NCF emitido en el período salvo los cancelados (608), con lo
 * cobrado hasta el cierre del período por forma de pago y el resto como venta a crédito.
 */
@SpringBootTest
@Transactional
class Dgii607AbonadasIntegrationTest {

    private static final YearMonth PERIODO = YearMonth.of(2031, 5);

    @Autowired private SaleRepository saleRepository;
    @Autowired private SalePaymentRepository salePaymentRepository;
    @Autowired private CreditNoteRepository creditNoteRepository;
    @Autowired private DgiiReportService dgiiReportService;
    @Autowired private EntityManager entityManager;

    @Test
    void facturaAbonadaConNcf_vaAl607ConLoCobradoYElRestoACredito() {
        Sale abonada = sale("B0199980001", SaleStatus.PARTIALLY_PAID, LocalDateTime.of(2031, 5, 10, 9, 0),
                "1000.00", "0.00", "180.00");
        payment(abonada, LocalDateTime.of(2031, 5, 10, 9, 5), "300.00", PaymentMethod.CASH);
        payment(abonada, LocalDateTime.of(2031, 5, 20, 15, 0), "200.00", PaymentMethod.TRANSFER);
        // Último instante del mes: todavía cuenta.
        payment(abonada, LocalDateTime.of(2031, 5, 31, 23, 59, 59, 500_000_000), "100.00", PaymentMethod.CARD);
        // Abono del mes siguiente: no cambia el 607 de mayo.
        payment(abonada, LocalDateTime.of(2031, 6, 2, 10, 0), "380.00", PaymentMethod.CASH);
        flushAndClear();

        Dgii607RowDto row = rowFor(dgiiReportService.build607(PERIODO), "B0199980001");

        assertThat(row.getMontoFacturado()).isEqualByComparingTo("1000.00");
        assertThat(row.getItbisFacturado()).isEqualByComparingTo("180.00");
        assertThat(row.getEfectivo()).isEqualByComparingTo("300.00");
        assertThat(row.getChequeTransferencia()).isEqualByComparingTo("200.00");
        assertThat(row.getTarjeta()).isEqualByComparingTo("100.00");
        assertThat(row.getOtrasFormas()).isEqualByComparingTo("0.00");
        assertThat(row.getVentaCredito()).isEqualByComparingTo("580.00");
    }

    @Test
    void facturaConNcfSinNingunCobroEnElPeriodo_vaEnteraACredito() {
        Sale pendiente = sale("B0199980002", SaleStatus.PENDING, LocalDateTime.of(2031, 5, 3, 11, 0),
                "500.00", "100.00", "90.00");
        payment(pendiente, LocalDateTime.of(2031, 6, 1, 0, 0), "690.00", PaymentMethod.CASH);
        flushAndClear();

        Dgii607RowDto row = rowFor(dgiiReportService.build607(PERIODO), "B0199980002");

        assertThat(row.getMontoFacturado()).isEqualByComparingTo("600.00");
        assertThat(row.getEfectivo()).isEqualByComparingTo("0.00");
        assertThat(row.getVentaCredito()).isEqualByComparingTo("690.00");
    }

    @Test
    void facturaCompletada_sigueIgualYCanceladaSoloVaAl608() {
        Sale completada = sale("B0199980003", SaleStatus.COMPLETED, LocalDateTime.of(2031, 5, 15, 12, 0),
                "200.00", "0.00", "36.00");
        payment(completada, LocalDateTime.of(2031, 5, 15, 12, 1), "236.00", PaymentMethod.CARD);
        sale("B0199980004", SaleStatus.CANCELED, LocalDateTime.of(2031, 5, 16, 12, 0), "100.00", "0.00", "18.00");
        // Sin NCF (borrador abonado): no es comprobante, no se reporta.
        Sale borrador = sale(null, SaleStatus.PARTIALLY_PAID, LocalDateTime.of(2031, 5, 17, 12, 0),
                "100.00", "0.00", "18.00");
        payment(borrador, LocalDateTime.of(2031, 5, 17, 12, 1), "50.00", PaymentMethod.CASH);
        // Emitida en otro período: no entra en mayo.
        sale("B0199980005", SaleStatus.PARTIALLY_PAID, LocalDateTime.of(2031, 4, 30, 23, 0), "100.00", "0.00", "18.00");
        flushAndClear();

        Dgii607ReportDto report = dgiiReportService.build607(PERIODO);
        Dgii607RowDto row = rowFor(report, "B0199980003");
        assertThat(row.getMontoFacturado()).isEqualByComparingTo("200.00");
        assertThat(row.getTarjeta()).isEqualByComparingTo("236.00");
        assertThat(row.getVentaCredito()).isEqualByComparingTo("0.00");

        List<String> ncfs = report.getRows().stream().map(Dgii607RowDto::getNcf).toList();
        assertThat(ncfs).doesNotContain("B0199980004", "B0199980005");
        assertThat(ncfs).doesNotContainNull();
        assertThat(ncfs).doesNotHaveDuplicates();

        Dgii608ReportDto report608 = dgiiReportService.build608(PERIODO);
        assertThat(report608.getRows()).extracting(r -> r.getNcf()).contains("B0199980004");
    }

    @Test
    void abonadaConNotaDeCredito_llevaLoFacturadoOriginalYLaNotaAparte() {
        // Factura original 1180 (1000 + 180 ITBIS); nota de crédito por 590; vigente 590.
        Sale abonada = sale("B0199980006", SaleStatus.PARTIALLY_PAID, LocalDateTime.of(2031, 5, 5, 10, 0),
                "500.00", "0.00", "90.00");
        payment(abonada, LocalDateTime.of(2031, 5, 5, 10, 1), "400.00", PaymentMethod.CASH);

        CreditNote cn = new CreditNote();
        cn.setSale(abonada);
        cn.setNcf("B0499980001");
        cn.setNcfModificado("B0199980006");
        cn.setCreatedAt(LocalDateTime.of(2031, 5, 12, 9, 0));
        cn.setMontoGravadoTotal(new BigDecimal("500.00"));
        cn.setMontoExento(new BigDecimal("0.00"));
        cn.setTotalItbis(new BigDecimal("90.00"));
        cn.setMontoTotal(new BigDecimal("590.00"));
        creditNoteRepository.save(cn);
        flushAndClear();

        Dgii607ReportDto report = dgiiReportService.build607(PERIODO);
        Dgii607RowDto factura = rowFor(report, "B0199980006");
        Dgii607RowDto nota = rowFor(report, "B0499980001");

        assertThat(factura.getMontoFacturado()).isEqualByComparingTo("1000.00");
        assertThat(factura.getItbisFacturado()).isEqualByComparingTo("180.00");
        assertThat(factura.getEfectivo()).isEqualByComparingTo("400.00");
        assertThat(factura.getVentaCredito()).isEqualByComparingTo("780.00");

        assertThat(nota.getNcfModificado()).isEqualTo("B0199980006");
        assertThat(nota.getMontoFacturado()).isEqualByComparingTo("500.00");
        assertThat(nota.getItbisFacturado()).isEqualByComparingTo("90.00");
        assertThat(report.getRows().stream().filter(r -> "B0199980006".equals(r.getNcf())).count()).isEqualTo(1);
    }

    private Sale sale(String ncf, SaleStatus status, LocalDateTime date,
                      String gravado, String exento, String itbis) {
        Sale sale = new Sale();
        sale.setSaleDate(date);
        sale.setStatus(status);
        sale.setTipoComprobante("01");
        sale.setNcf(ncf);
        BigDecimal g = new BigDecimal(gravado);
        BigDecimal e = new BigDecimal(exento);
        BigDecimal i = new BigDecimal(itbis);
        sale.setMontoGravadoTotal(g);
        sale.setMontoExento(e);
        sale.setTotalItbis(i);
        sale.setMontoTotal(g.add(e).add(i));
        sale.setPaidAmount(BigDecimal.ZERO);
        return saleRepository.save(sale);
    }

    private void payment(Sale sale, LocalDateTime date, String amount, PaymentMethod method) {
        SalePayment payment = new SalePayment();
        payment.setSale(sale);
        payment.setPaymentDate(date);
        payment.setAmount(new BigDecimal(amount));
        payment.setPaymentMethod(method);
        payment.setCreatedAt(date);
        salePaymentRepository.save(payment);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private static Dgii607RowDto rowFor(Dgii607ReportDto report, String ncf) {
        return report.getRows().stream()
                .filter(r -> ncf.equals(r.getNcf()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Falta en el 607: " + ncf));
    }
}
