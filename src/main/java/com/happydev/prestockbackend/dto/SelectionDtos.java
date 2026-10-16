package com.happydev.prestockbackend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Selección de fotos por el cliente (fase 3). */
public final class SelectionDtos {

    private SelectionDtos() {}

    /** Lo que ve administración en el detalle de la orden. */
    public record AdminSelection(
            String url,
            int quota,
            LocalDateTime expiresAt,
            boolean expired,
            int previewCount,
            int selectedCount,
            LocalDateTime submittedAt,
            int extraCount,
            BigDecimal extraUnitPrice
    ) {}

    /** Valores sugeridos antes de generar el enlace (cupo según los combos de la orden). */
    public record SelectionSuggestion(int suggestedQuota, int previewCount, BigDecimal extraUnitPrice) {}

    public record CreateSelectionRequest(
            @NotNull @Min(0) @Max(5000) Integer quota,
            @Min(1) @Max(180) Integer expiresInDays
    ) {}

    /** Lo que ve el cliente en la web. Sin montos de la orden ni datos de otras órdenes. */
    public record PublicSelection(
            String studio,
            String title,
            String customerFirstName,
            int quota,
            /** Precio con ITBIS de cada foto extra; null si no se venden extras */
            BigDecimal extraUnitPrice,
            List<String> photos,
            List<String> selected,
            boolean submitted,
            LocalDateTime expiresAt
    ) {}

    public record SelectionUpdate(@NotNull List<String> selected) {}

    public record SelectionSubmit(@NotNull List<String> selected, boolean acceptExtra) {}
}
