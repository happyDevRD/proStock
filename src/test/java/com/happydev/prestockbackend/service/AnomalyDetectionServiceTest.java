package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.util.MoneyFormatUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class AnomalyDetectionServiceTest {

    private final Locale original = Locale.getDefault();

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(original);
    }

    private static Object[] day(LocalDate date, double revenue) {
        return new Object[]{Date.valueOf(date), 1L, BigDecimal.valueOf(revenue), BigDecimal.ZERO};
    }

    @Test
    void picosDeIngresos_soloLosDeLosUltimos30Dias() {
        LocalDate today = LocalDate.of(2026, 11, 3);
        List<Object[]> trend = new ArrayList<>();
        for (int i = 59; i >= 1; i--) {
            trend.add(day(today.minusDays(i), 1000));
        }
        trend.set(59 - 45, day(today.minusDays(45), 124903));   // pico de hace 45 días: fuera de lo mostrado
        trend.set(59 - 10, day(today.minusDays(10), 98000));    // pico de hace 10 días: se muestra

        List<AnomalyDetectionService.AnomalyDto> spikes = AnomalyDetectionService.revenueSpikes(trend, today);

        assertThat(spikes).extracting(AnomalyDetectionService.AnomalyDto::date)
                .containsExactly(today.minusDays(10).toString());
    }

    @Test
    void montosConFormatoDominicano_independienteDelLocale() {
        Locale.setDefault(new Locale("es", "ES"));
        LocalDate today = LocalDate.of(2026, 11, 3);
        List<Object[]> trend = new ArrayList<>();
        for (int i = 20; i >= 1; i--) {
            trend.add(day(today.minusDays(i), 1000));
        }
        trend.add(day(today, 124903));

        String description = AnomalyDetectionService.revenueSpikes(trend, today).get(0).description();

        assertThat(description).contains("RD$124,903.00").doesNotContain("124903,00");
        assertThat(MoneyFormatUtils.rd(new BigDecimal("1234567.5"))).isEqualTo("RD$1,234,567.50");
        assertThat(MoneyFormatUtils.rd((BigDecimal) null)).isEqualTo("RD$0.00");
    }
}
