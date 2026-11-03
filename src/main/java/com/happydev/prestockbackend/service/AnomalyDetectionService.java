package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.entity.Sale;
import com.happydev.prestockbackend.repository.SaleRepository;
import com.happydev.prestockbackend.util.MoneyFormatUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Detección estadística de anomalías en ventas e inventario.
 * No usa LLM — las reglas son determinísticas; Gemini puede enriquecer la explicación si está disponible.
 */
@Service
public class AnomalyDetectionService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /** Ventana de lo que se muestra (la pantalla dice "últimos 30 días"). */
    static final int REPORT_WINDOW_DAYS = 30;
    /** Historial para la media y la desviación de los picos de ingresos. */
    static final int BASELINE_WINDOW_DAYS = 60;

    private final SaleRepository saleRepository;

    public AnomalyDetectionService(SaleRepository saleRepository) {
        this.saleRepository = saleRepository;
    }

    public List<AnomalyDto> detectAll() {
        List<AnomalyDto> anomalies = new ArrayList<>();
        anomalies.addAll(detectHighDiscounts());
        anomalies.addAll(detectRevenueSpikes());
        return anomalies;
    }

    /** Detecta ventas completadas con descuento superior al 30% del total. */
    private List<AnomalyDto> detectHighDiscounts() {
        LocalDateTime since = LocalDateTime.now().minusDays(REPORT_WINDOW_DAYS);
        List<Sale> highDiscount = saleRepository.findCompletedWithHighDiscount(
                BigDecimal.valueOf(1), since);

        List<AnomalyDto> result = new ArrayList<>();
        for (Sale sale : highDiscount) {
            if (sale.getMontoTotal().compareTo(BigDecimal.ZERO) == 0) continue;
            BigDecimal ratio = sale.getDiscountAmount()
                    .multiply(BigDecimal.valueOf(100))
                    .divide(sale.getMontoTotal().add(sale.getDiscountAmount()), 2, RoundingMode.HALF_UP);
            if (ratio.compareTo(BigDecimal.valueOf(30)) >= 0) {
                String label = sale.getNcf() != null ? sale.getNcf() : "#" + sale.getId();
                result.add(new AnomalyDto(
                        "DISCOUNT",
                        "Descuento inusual (" + ratio.stripTrailingZeros().toPlainString() + "%)",
                        "La factura " + label + " tiene un descuento de "
                                + MoneyFormatUtils.rd(sale.getDiscountAmount())
                                + " (" + ratio.toPlainString() + "% del total) — superior al umbral del 30%.",
                        sale.getSaleDate() != null ? sale.getSaleDate().format(FMT) : null,
                        ratio.compareTo(BigDecimal.valueOf(50)) >= 0 ? "HIGH" : "MEDIUM"
                ));
            }
        }
        return result;
    }

    /**
     * Detecta días de los últimos 30 con ingresos que superan en más de 2σ la media diaria de los
     * últimos 60 (la media necesita más historial que la ventana que se muestra).
     */
    private List<AnomalyDto> detectRevenueSpikes() {
        LocalDate today = LocalDate.now();
        List<Object[]> trend = saleRepository.findCompletedDailyTrend(
                today.minusDays(BASELINE_WINDOW_DAYS).atStartOfDay());
        return revenueSpikes(trend, today);
    }

    /** Filas [día, cantidad, ingresos, itbis] de findCompletedDailyTrend. */
    static List<AnomalyDto> revenueSpikes(List<Object[]> trend, LocalDate today) {
        if (trend.size() < 7) return List.of();

        double[] revenues = trend.stream()
                .mapToDouble(row -> ((Number) row[2]).doubleValue())
                .toArray();

        double mean = 0;
        for (double v : revenues) mean += v;
        mean /= revenues.length;

        double variance = 0;
        for (double v : revenues) variance += (v - mean) * (v - mean);
        double stdDev = Math.sqrt(variance / revenues.length);
        if (stdDev < 1) return List.of();

        LocalDate reportFrom = today.minusDays(REPORT_WINDOW_DAYS);
        List<AnomalyDto> result = new ArrayList<>();
        for (Object[] row : trend) {
            LocalDate day = toLocalDate(row[0]);
            if (day != null && day.isBefore(reportFrom)) continue;
            double revenue = ((Number) row[2]).doubleValue();
            double zScore = (revenue - mean) / stdDev;
            if (zScore > 2.0) {
                String dayText = day != null ? day.toString() : String.valueOf(row[0]);
                result.add(new AnomalyDto(
                        "REVENUE_SPIKE",
                        "Pico de ingresos",
                        "El día " + dayText + " generó " + MoneyFormatUtils.rd(revenue)
                                + " — " + String.format(Locale.US, "%.1f", zScore)
                                + " desviaciones sobre la media diaria de los últimos "
                                + BASELINE_WINDOW_DAYS + " días (" + MoneyFormatUtils.rd(mean) + ").",
                        dayText,
                        zScore > 3.0 ? "HIGH" : "MEDIUM"
                ));
            }
        }
        return result;
    }

    private static LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate d) return d;
        if (value instanceof java.sql.Date d) return d.toLocalDate();
        if (value instanceof LocalDateTime dt) return dt.toLocalDate();
        if (value instanceof java.util.Date d) return new java.sql.Date(d.getTime()).toLocalDate();
        try {
            return value != null ? LocalDate.parse(value.toString().substring(0, 10)) : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    public record AnomalyDto(
            String type,
            String title,
            String description,
            String date,
            String severity
    ) {}
}
