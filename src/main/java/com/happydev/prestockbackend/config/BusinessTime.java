package com.happydev.prestockbackend.config;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.TimeZone;

/**
 * Zona horaria del negocio.
 *
 * Las fechas-hora se guardan como LocalDateTime (sin zona) en la hora local
 * del negocio, así que la JVM tiene que correr en esa zona: un contenedor en
 * UTC guardaba las ventas 4 h adelantadas y pasaba al día siguiente a las 8 p. m.
 */
public final class BusinessTime {

    public static final String DEFAULT_ZONE = "America/Santo_Domingo";
    public static final String ZONE_ENV = "APP_TIMEZONE";

    private BusinessTime() {
    }

    /** Fija la zona por defecto de la JVM (y de las sesiones JDBC) antes de arrancar Spring. */
    public static ZoneId applyDefaultZone(String configured) {
        ZoneId zone = ZoneId.of(configured == null || configured.isBlank() ? DEFAULT_ZONE : configured.trim());
        TimeZone.setDefault(TimeZone.getTimeZone(zone));
        return zone;
    }

    /**
     * Interpreta un texto ISO como LocalDateTime en la zona actual.
     * Si trae zona u offset ("...Z", "...-04:00", lo que manda toISOString() del
     * navegador), se convierte a la hora local en lugar de descartar la zona.
     * Una fecha sola ("YYYY-MM-DD") es el inicio de ese día.
     */
    public static LocalDateTime parseLocalDateTime(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String value = text.trim();
        try {
            TemporalAccessor parsed = DateTimeFormatter.ISO_DATE_TIME.parse(value);
            LocalDateTime local = LocalDateTime.from(parsed);
            if (parsed.isSupported(ChronoField.OFFSET_SECONDS)) {
                return OffsetDateTime.of(local, java.time.ZoneOffset.ofTotalSeconds(parsed.get(ChronoField.OFFSET_SECONDS)))
                        .atZoneSameInstant(ZoneId.systemDefault())
                        .toLocalDateTime();
            }
            return local;
        } catch (DateTimeParseException notDateTime) {
            return java.time.LocalDate.parse(value).atStartOfDay();
        }
    }
}
