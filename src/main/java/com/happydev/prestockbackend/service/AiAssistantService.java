package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.entity.SaleStatus;
import com.happydev.prestockbackend.repository.SaleRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Asistente conversacional que usa Gemini para responder preguntas en lenguaje natural
 * sobre los datos del negocio (ventas, inventario, reportes).
 */
@Service
public class AiAssistantService {

    private static final String SYSTEM_PROMPT = """
            Eres un asistente de análisis de negocio para ProStock, un sistema ERP para empresas dominicanas.
            Solo respondes en español. Eres conciso, preciso y profesional.
            Tienes acceso al contexto de datos del negocio que se te proporcionará.
            Si la pregunta no está relacionada con los datos del negocio, responde que solo puedes
            ayudar con información sobre ventas, inventario y reportes del negocio.
            Formatea los números con dos decimales y usa "RD$" para montos monetarios.
            No inventes datos que no estén en el contexto.
            """;

    private final SaleRepository saleRepository;
    private final GeminiService geminiService;

    public AiAssistantService(SaleRepository saleRepository, GeminiService geminiService) {
        this.saleRepository = saleRepository;
        this.geminiService = geminiService;
    }

    public String answer(String question) {
        String context = buildContext();
        String prompt = "Contexto actual del negocio:\n" + context + "\n\nPregunta del usuario: " + question;
        String response = geminiService.generate(SYSTEM_PROMPT, prompt);
        if (response == null) {
            return "El asistente IA no está disponible. Configura la clave de Gemini en Ajustes > Integraciones > gemini / api_key.";
        }
        return response;
    }

    private String buildContext() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfMonth = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
        LocalDateTime startOf7Days = now.minusDays(7);
        LocalDateTime startOf30Days = now.minusDays(30);
        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        BigDecimal revenueThisMonth = safe(saleRepository.sumCompletedRevenue(startOfMonth, now));
        BigDecimal revenue7Days = safe(saleRepository.sumCompletedRevenue(startOf7Days, now));
        BigDecimal revenue30Days = safe(saleRepository.sumCompletedRevenue(startOf30Days, now));
        long salesThisMonth = saleRepository.countInRange(SaleStatus.COMPLETED, startOfMonth, now);
        long sales7Days = saleRepository.countInRange(SaleStatus.COMPLETED, startOf7Days, now);
        long partiallyPaid = saleRepository.countPartiallyPaid();
        BigDecimal pendingBalance = safe(saleRepository.sumPendingBalance());

        var topProducts = saleRepository.findTopCustomersByRevenue(startOf30Days, now);

        StringBuilder sb = new StringBuilder();
        sb.append("Fecha actual: ").append(now.format(dateFmt)).append("\n");
        sb.append("Ventas completadas este mes: ").append(salesThisMonth).append(" | Ingresos: RD$")
                .append(revenueThisMonth.setScale(2, RoundingMode.HALF_UP)).append("\n");
        sb.append("Ventas completadas últimos 7 días: ").append(sales7Days).append(" | Ingresos: RD$")
                .append(revenue7Days.setScale(2, RoundingMode.HALF_UP)).append("\n");
        sb.append("Ingresos últimos 30 días: RD$").append(revenue30Days.setScale(2, RoundingMode.HALF_UP)).append("\n");
        sb.append("Facturas pendientes de cobro: ").append(partiallyPaid)
                .append(" | Balance pendiente: RD$").append(pendingBalance.setScale(2, RoundingMode.HALF_UP)).append("\n");

        if (!topProducts.isEmpty()) {
            sb.append("Top clientes por ingresos (últimos 30 días):\n");
            for (Object[] row : topProducts) {
                sb.append("  - ").append(row[1]).append(": RD$")
                        .append(((Number) row[2]).doubleValue() == 0 ? "0.00"
                                : String.format("%.2f", ((Number) row[2]).doubleValue()))
                        .append("\n");
            }
        }

        return sb.toString();
    }

    private BigDecimal safe(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
