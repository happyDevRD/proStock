package com.happydev.prestockbackend.dto;

import com.happydev.prestockbackend.entity.ServiceOrderType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * "Mis trabajos": lo que ve el personal de producción (técnico, fotógrafo, editor) de las órdenes que tiene asignadas.
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
            /** Fin de la sesión (exclusivo) */
            LocalDateTime appointmentEnd,
            boolean appointmentAllDay,
            LocalDate estimatedDelivery,
            String currentStage,
            /** Tipo de orden: nombra roles y etapas en pantalla (en un taller FOTOGRAFO = técnico) */
            ServiceOrderType orderType,
            /** FOTOGRAFO y/o EDITOR: qué le toca a este usuario en la orden */
            List<String> myRoles,
            String photographerName,
            String editorName,
            String workFolder,
            /** Archivos por subcarpeta ("1 Camara" → 412); null si no hay carpeta */
            java.util.Map<String, Integer> folderCounts,
            String internalNotes,
            /** Servicios sin precio, p. ej. "1 x Combo XV Años 2" */
            List<String> services,
            List<MyWorkNote> notes,
            /** Siguiente paso que este usuario puede marcar; null si no le toca nada ahora */
            MyWorkAction nextAction
    ) {}

    public record MyWorkNote(String content, String author, LocalDateTime createdAt) {}

    /** Agenda personal: citas donde el usuario es fotógrafo o editor, y bloqueos que le afectan. */
    public record MyCalendarResponse(boolean linked, List<MyCalendarItem> appointments,
                                     List<com.happydev.prestockbackend.dto.ScheduleDtos.CalendarBlockDto> blocks) {}

    public record MyCalendarItem(
            Long id,
            String orderNumber,
            String title,
            String customerName,
            LocalDateTime start,
            LocalDateTime end,
            boolean allDay,
            String currentStage,
            ServiceOrderType orderType,
            String status,
            List<String> myRoles
    ) {}

    /**
     * Paso que el usuario puede marcar. {@code noteLabel}: si no es null, el paso pide una nota
     * con ese título (p. ej. «Diagnóstico») que se envía al avanzar.
     */
    public record MyWorkAction(String stage, String label, String noteLabel) {}

    /** Cuerpo opcional de «avanzar»: la nota que piden algunos pasos. */
    public record MyWorkAdvanceRequest(
            @jakarta.validation.constraints.Size(max = 2000) String note) {}

    public record MyWorkNoteRequest(@jakarta.validation.constraints.NotBlank String content) {}
}
