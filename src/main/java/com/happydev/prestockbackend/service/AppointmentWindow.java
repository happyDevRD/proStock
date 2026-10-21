package com.happydev.prestockbackend.service;

import org.springframework.lang.Nullable;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Inicio y fin (exclusivo) de una cita o un bloqueo, ya normalizados.
 *
 * <ul>
 *   <li>Día completo: empieza a medianoche y termina a medianoche del día siguiente al último día.</li>
 *   <li>Con hora: sin fin dura {@link #DEFAULT_DURATION}; el fin debe ser posterior al inicio.</li>
 * </ul>
 */
public record AppointmentWindow(LocalDateTime start, LocalDateTime end, boolean allDay) {

    public static final Duration DEFAULT_DURATION = Duration.ofHours(1);
    static final Duration MAX_DURATION = Duration.ofDays(14);

    /** null si no hay fecha de inicio (orden sin cita). */
    @Nullable
    public static AppointmentWindow of(@Nullable LocalDateTime start, @Nullable LocalDateTime end,
                                       @Nullable Boolean allDay) {
        if (start == null) return null;
        AppointmentWindow w;
        if (Boolean.TRUE.equals(allDay)) {
            LocalDateTime dayStart = start.toLocalDate().atStartOfDay();
            LocalDateTime dayEnd = end == null ? null
                    : end.toLocalTime().equals(LocalTime.MIDNIGHT) ? end : end.toLocalDate().plusDays(1).atStartOfDay();
            if (dayEnd == null || !dayEnd.isAfter(dayStart)) dayEnd = dayStart.plusDays(1);
            w = new AppointmentWindow(dayStart, dayEnd, true);
        } else {
            LocalDateTime finish = end == null ? start.plus(DEFAULT_DURATION) : end;
            if (!finish.isAfter(start)) {
                throw new IllegalArgumentException("La hora de fin debe ser posterior a la de inicio.");
            }
            w = new AppointmentWindow(start, finish, false);
        }
        if (Duration.between(w.start, w.end).compareTo(MAX_DURATION) > 0) {
            throw new IllegalArgumentException("Una cita o bloqueo no puede durar más de 14 días.");
        }
        return w;
    }

    public boolean overlaps(LocalDateTime otherStart, LocalDateTime otherEnd) {
        return start.isBefore(otherEnd) && end.isAfter(otherStart);
    }
}
