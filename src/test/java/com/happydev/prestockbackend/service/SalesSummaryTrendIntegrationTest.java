package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.dto.SaleSummaryDto;
import com.happydev.prestockbackend.entity.Sale;
import com.happydev.prestockbackend.entity.SaleStatus;
import com.happydev.prestockbackend.repository.SaleRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/** La tendencia diaria del resumen de ventas debe seguir el período pedido, no solo los últimos 7 días. */
@SpringBootTest
@Transactional
class SalesSummaryTrendIntegrationTest {

    @Autowired private SaleRepository saleRepository;
    @Autowired private SaleService saleService;
    @Autowired private EntityManager entityManager;

    private void venta(LocalDateTime fecha, String total) {
        Sale s = new Sale();
        s.setSaleDate(fecha);
        s.setStatus(SaleStatus.COMPLETED);
        s.setMontoGravadoTotal(new BigDecimal(total));
        s.setMontoExento(BigDecimal.ZERO);
        s.setTotalItbis(BigDecimal.ZERO);
        s.setMontoTotal(new BigDecimal(total));
        s.setPaidAmount(new BigDecimal(total));
        saleRepository.save(s);
    }

    @Test
    void tendencia_cubreElPeriodoCompleto() {
        LocalDate base = LocalDate.of(2032, 4, 30);
        venta(base.minusDays(20).atTime(10, 0), "500.00");   // dentro de 30 días, fuera de 7
        venta(base.minusDays(2).atTime(11, 0), "300.00");
        venta(base.minusDays(45).atTime(9, 0), "999.00");    // fuera del período
        entityManager.flush();

        SaleSummaryDto r = saleService.getSalesSummary(
                base.minusDays(29).atStartOfDay(), base.atTime(LocalTime.MAX), null);

        assertThat(r.revenueTrend()).hasSize(2);
        BigDecimal suma = r.revenueTrend().stream()
                .map(d -> d.revenue()).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(suma).isEqualByComparingTo("800.00");
    }
}
