package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.entity.ServiceOrderStatus;
import com.happydev.prestockbackend.entity.ServiceOrderType;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Pipeline de etapas por tipo de orden — debe mantenerse alineado con
 * {@code proStockFront/src/lib/serviceOrders.ts}.
 */
public final class ServiceOrderStageCatalog {

    public record StageDefinition(String key, ServiceOrderStatus status) {}

    private static final Map<ServiceOrderType, List<StageDefinition>> PIPELINES = Map.of(
            ServiceOrderType.PHOTOGRAPHY, List.of(
                    new StageDefinition("SCHEDULED", ServiceOrderStatus.OPEN),
                    new StageDefinition("SESSION", ServiceOrderStatus.IN_PROGRESS),
                    new StageDefinition("SELECTION", ServiceOrderStatus.WAITING_CLIENT),
                    new StageDefinition("EDITING", ServiceOrderStatus.IN_PROGRESS),
                    new StageDefinition("READY", ServiceOrderStatus.READY),
                    new StageDefinition("DELIVERED", ServiceOrderStatus.COMPLETED)
            ),
            ServiceOrderType.REPAIR, List.of(
                    new StageDefinition("RECEIVED", ServiceOrderStatus.OPEN),
                    new StageDefinition("DIAGNOSIS", ServiceOrderStatus.IN_PROGRESS),
                    new StageDefinition("BUDGET_SENT", ServiceOrderStatus.WAITING_CLIENT),
                    new StageDefinition("APPROVED", ServiceOrderStatus.IN_PROGRESS),
                    new StageDefinition("IN_REPAIR", ServiceOrderStatus.IN_PROGRESS),
                    new StageDefinition("QUALITY_CHECK", ServiceOrderStatus.IN_PROGRESS),
                    new StageDefinition("READY", ServiceOrderStatus.READY),
                    new StageDefinition("DELIVERED", ServiceOrderStatus.COMPLETED)
            ),
            ServiceOrderType.GENERAL, List.of(
                    new StageDefinition("RECEIVED", ServiceOrderStatus.OPEN),
                    new StageDefinition("IN_PROGRESS", ServiceOrderStatus.IN_PROGRESS),
                    new StageDefinition("READY", ServiceOrderStatus.READY),
                    new StageDefinition("DELIVERED", ServiceOrderStatus.COMPLETED)
            )
    );

    private ServiceOrderStageCatalog() {}

    public static List<StageDefinition> pipeline(ServiceOrderType type) {
        return PIPELINES.getOrDefault(type, List.of());
    }

    public static Optional<StageDefinition> findStage(ServiceOrderType type, String stageKey) {
        return pipeline(type).stream()
                .filter(s -> s.key().equals(stageKey))
                .findFirst();
    }

    public static Optional<StageDefinition> nextStage(ServiceOrderType type, String currentStage) {
        List<StageDefinition> stages = pipeline(type);
        for (int i = 0; i < stages.size(); i++) {
            if (stages.get(i).key().equals(currentStage) && i < stages.size() - 1) {
                return Optional.of(stages.get(i + 1));
            }
        }
        return Optional.empty();
    }

    public static void validateAdvance(
            ServiceOrderType orderType,
            String currentStage,
            String targetStage,
            ServiceOrderStatus targetStatus
    ) {
        StageDefinition target = findStage(orderType, targetStage)
                .orElseThrow(() -> new IllegalArgumentException(
                        "La etapa '" + targetStage + "' no es válida para el tipo " + orderType.name()));

        if (target.status() != targetStatus) {
            throw new IllegalArgumentException(
                    "El estado " + targetStatus.name() + " no corresponde a la etapa " + targetStage);
        }

        Optional<StageDefinition> expectedNext = nextStage(orderType, currentStage);
        if (expectedNext.isEmpty()) {
            throw new IllegalStateException("La orden ya está en la última etapa del pipeline.");
        }
        if (!expectedNext.get().key().equals(targetStage)) {
            throw new IllegalArgumentException(
                    "Solo se puede avanzar a la siguiente etapa: " + expectedNext.get().key());
        }
    }
}
