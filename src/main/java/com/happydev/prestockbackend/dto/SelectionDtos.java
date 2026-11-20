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
            BigDecimal extraUnitPrice,
            /** Nombres de las previas marcadas por el cliente (borrador o confirmadas) */
            List<String> selectedFiles,
            /** Cuándo se generó el enlace actual y la última vez que el cliente marcó algo */
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            /** Usuario del cliente en el portal */
            String username,
            /** Contraseña en claro: SOLO al generar el acceso o restablecerla; si no, null */
            String password,
            LocalDateTime lastLoginAt,
            /** Bloqueado por intentos fallidos */
            boolean locked,
            /** NONE · PENDING · APPROVED · REJECTED */
            String extraStatus,
            /** Previas adicionales (las que pasan del cupo) */
            List<String> extraFiles,
            LocalDateTime extraDecidedAt,
            String extraDecidedBy,
            String extraNotes,
            /** Saldo de la orden (solo con adicionales pendientes) */
            BigDecimal balance,
            /** Con adicionales pendientes: true si el saldo está cubierto y se pueden autorizar */
            boolean extrasPayable,
            Long orderId,
            String orderNumber,
            String customerName,
            /** Enlace directo (entra sin contraseña): el que se le manda al cliente */
            String accessUrl,
            /** ENLACE · CONTRASENA (cómo entró la última vez) */
            String lastLoginMethod,
            /** Dispositivo de la última entrada ("iPhone · Safari") */
            String lastLoginDevice
    ) {}

    /** Avance de la generación automática de previas desde "1 Camara". */
    public record PreviewProgress(boolean running, int total, int done, int created, int failed,
                                  String error, LocalDateTime startedAt, LocalDateTime finishedAt) {}

    public record ApproveExtrasRequest(boolean force) {}

    public record RejectExtrasRequest(@jakarta.validation.constraints.Size(max = 500) String reason) {}

    public record LoginRequest(@NotNull @jakarta.validation.constraints.Size(max = 80) String username,
                               @NotNull @jakarta.validation.constraints.Size(max = 20) String password) {}

    /**
     * Sesión firmada. El portal la recibe en una cookie HttpOnly; {@code session} se mantiene por
     * compatibilidad con las rutas antiguas ({@code /api/seleccion/{session}/...}).
     */
    public record LoginResponse(String session, String username) {}

    /** Entrada con el enlace directo ({@code k_<id>_<firma>}). */
    public record AccessRequest(@NotNull @jakarta.validation.constraints.Size(max = 120) String key) {}

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
            LocalDateTime expiresAt,
            /** Tras confirmar: las fotos adicionales (pasan del cupo) */
            List<String> extraFiles,
            /** NONE · PENDING (esperan pago y autorización) · APPROVED · REJECTED */
            String extraStatus
    ) {}

    public record SelectionUpdate(@NotNull List<String> selected) {}

    public record SelectionSubmit(@NotNull List<String> selected, boolean acceptExtra) {}
}
