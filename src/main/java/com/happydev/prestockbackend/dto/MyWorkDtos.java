package com.happydev.prestockbackend.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * "Mis trabajos": lo que ve un fotógrafo o editor de las órdenes que tiene asignadas.
 * Sin montos, cobros ni facturas.
 */
public final class MyWorkDtos {

    private MyWorkDtos() {}

    /** linked = el usuario tiene un empleado vinculado; si no, no hay órdenes que mostrar. */
    public record MyWorkResponse(boolean linked, String employeeName, List<MyWorkOrder> orders) {}

    public record MyWorkOrder(
            Long id,
            String orderNumber,
            String title,
            String customerName,
            String customerPhone,
            LocalDateTime appointmentDate,
            LocalDate estimatedDelivery,
            String currentStage,
            /** FOTOGRAFO y/o EDITOR: qué le toca a este usuario en la orden */
            List<String> myRoles,
            String photographerName,
            String editorName,
            String workFolder,
            String internalNotes,
            /** Servicios sin precio, p. ej. "1 x Combo XV Años 2" */
            List<String> services,
            List<MyWorkNote> notes,
            /** Siguiente paso que este usuario puede marcar; null si no le toca nada ahora */
            MyWorkAction nextAction
    ) {}

    public record MyWorkNote(String content, String author, LocalDateTime createdAt) {}

    public record MyWorkAction(String stage, String label) {}

    public record MyWorkNoteRequest(@jakarta.validation.constraints.NotBlank String content) {}
}
