package com.happydev.prestockbackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/** Agenda de citas: disponibilidad y bloqueo de fechas. Ver documentacion/agenda-citas.md. */
public final class ScheduleDtos {

    private ScheduleDtos() {}

    /**
     * Algo que ocupa la agenda. kind = ORDER (otra cita) o BLOCK (fecha bloqueada).
     * {@code employeeName} es el fotógrafo de la cita o la persona del bloqueo (null = todo el estudio).
     */
    public record ScheduleItem(
            String kind,
            Long id,
            String label,
            String detail,
            LocalDateTime start,
            LocalDateTime end,
            boolean allDay,
            String employeeName
    ) {}

    /**
     * conflicts: el fotógrafo ya tiene cita o la fecha está bloqueada (para el estudio o para él).
     * sameTime: otras citas del estudio a esa hora con otro fotógrafo o sin asignar; solo informativo.
     */
    public record AvailabilityResponse(List<ScheduleItem> conflicts, List<ScheduleItem> sameTime) {}

    public record CalendarBlockDto(
            Long id,
            LocalDateTime start,
            LocalDateTime end,
            boolean allDay,
            Long employeeId,
            String employeeName,
            String reason,
            String createdBy
    ) {}

    /** {@code end} exclusivo (con allDay, medianoche del día siguiente al último; si falta, un día). */
    public record CreateCalendarBlockRequest(
            @NotNull LocalDateTime start,
            LocalDateTime end,
            Boolean allDay,
            Long employeeId,
            @NotBlank @Size(max = 200) String reason
    ) {}
}
