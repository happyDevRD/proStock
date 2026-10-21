package com.happydev.prestockbackend.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class AppointmentWindowTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 7);

    @Test
    void sinInicio_noHayCita() {
        assertNull(AppointmentWindow.of(null, DAY.atTime(10, 0), true));
    }

    @Test
    void sinFin_duraUnaHora() {
        var w = AppointmentWindow.of(DAY.atTime(10, 0), null, null);
        assertEquals(DAY.atTime(11, 0), w.end());
        assertFalse(w.allDay());
    }

    @Test
    void mediaHora() {
        var w = AppointmentWindow.of(DAY.atTime(10, 0), DAY.atTime(10, 30), false);
        assertEquals(DAY.atTime(10, 30), w.end());
    }

    @Test
    void finAntesDelInicio_esError() {
        assertThrows(IllegalArgumentException.class,
                () -> AppointmentWindow.of(DAY.atTime(10, 0), DAY.atTime(10, 0), false));
    }

    @Test
    void diaCompleto_vaDeMedianocheAMedianoche() {
        var w = AppointmentWindow.of(DAY.atTime(15, 30), null, true);
        assertEquals(DAY.atStartOfDay(), w.start());
        assertEquals(DAY.plusDays(1).atStartOfDay(), w.end());
        assertTrue(w.allDay());
    }

    @Test
    void variosDiasCompletos_finExclusivo() {
        var w = AppointmentWindow.of(DAY.atStartOfDay(), DAY.plusDays(2).atStartOfDay(), true);
        assertEquals(DAY.plusDays(2).atStartOfDay(), w.end());
        // Un fin con hora se redondea al final de ese día
        var w2 = AppointmentWindow.of(DAY.atStartOfDay(), DAY.plusDays(1).atTime(9, 0), true);
        assertEquals(DAY.plusDays(2).atStartOfDay(), w2.end());
    }

    @Test
    void masDeCatorceDias_esError() {
        LocalDateTime start = DAY.atStartOfDay();
        assertThrows(IllegalArgumentException.class,
                () -> AppointmentWindow.of(start, start.plusDays(15), true));
    }

    @Test
    void solape_finesExclusivos() {
        var w = AppointmentWindow.of(DAY.atTime(10, 0), DAY.atTime(11, 0), false);
        assertTrue(w.overlaps(DAY.atTime(10, 30), DAY.atTime(12, 0)));
        assertFalse(w.overlaps(DAY.atTime(11, 0), DAY.atTime(12, 0)));
        assertFalse(w.overlaps(DAY.atTime(9, 0), DAY.atTime(10, 0)));
    }
}
