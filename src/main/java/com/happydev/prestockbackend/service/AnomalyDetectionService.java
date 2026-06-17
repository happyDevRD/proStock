package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.entity.Sale;
import com.happydev.prestockbackend.repository.SaleRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Detección estadística de anomalías en ventas e inventario.
 * No usa LLM — las reglas son determinísticas; Gemini puede enriquecer la explicación si está disponible.
 */
@Service
public class AnomalyDetectionService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

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
        LocalDateTime since = LocalDateTime.now().minusDays(30);
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
                        "La factura " + label + " tiene un descuento de RD$"
                                + sale.getDiscountAmount().setScale(2, RoundingMode.HALF_UP)
                                + " (" + ratio + "% del total) — superior al umbral del 30%.",
                        sale.getSaleDate() != null ? sale.getSaleDate().format(FMT) : null,
                        ratio.compareTo(BigDecimal.valueOf(50)) >= 0 ? "HIGH" : "MEDIUM"
                ));
            }
        }
        return result;
    }

    /**
     * Detecta días con ingresos que superan en más de 2σ la media de los últimos 60 días.
     * Usa la tendencia diaria del repositorio.
     */
    private List<AnomalyDto> detectRevenueSpikes() {
        LocalDateTime since = LocalDateTime.now().minusDays(60);
        List<Object[]> trend = saleRepository.findCompletedDailyTrend(since);
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

        List<AnomalyDto> result = new ArrayList<>();
        for (Object[] row : trend) {
            double revenue = ((Number) row[2]).doubleValue();
            double zScore = (revenue - mean) / stdDev;
            if (zScore > 2.0) {
                String day = row[0].toString();
                result.add(new AnomalyDto(
                        "REVENUE_SPIKE",
                        "Pico de ingresos",
                        "El día " + day + " generó RD$" + String.format("%.2f", revenue)
                                + " — " + String.format("%.1f", zScore) + " desviaciones sobre la media diaria (RD$"
                                + String.format("%.2f", mean) + ").",
                        day,
                        zScore > 3.0 ? "HIGH" : "MEDIUM"
                ));
            }
        }
        return result;
    }

    public record AnomalyDto(
            String type,
            String title,
            String description,
            String date,
            String severity
    ) {}
}
