package com.happydev.prestockbackend.whatsapp;

import org.springframework.lang.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Locale;

/**
 * Textos que van dentro de las variables de las plantillas: teléfonos, fechas, montos y nombres.
 * Sin depender del Locale del servidor, para que el mensaje salga igual en cualquier contenedor.
 */
public final class WhatsAppText {

    private static final String[] DAYS = {"lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo"};
    private static final String[] MONTHS = {"enero", "febrero", "marzo", "abril", "mayo", "junio", "julio",
            "agosto", "septiembre", "octubre", "noviembre", "diciembre"};

    private WhatsAppText() {
    }

    /**
     * Número en formato internacional sin "+" (como lo pide la API), o null si no sirve.
     * Los números de RD de 10 dígitos (809/829/849) se completan con el código de país 1.
     */
    @Nullable
    public static String phone(@Nullable String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        String digits = trimmed.replaceAll("\\D", "");
        if (digits.startsWith("00")) digits = digits.substring(2);
        if (digits.length() == 10 && digits.matches("^8[024]9\\d{7}$")) return "1" + digits;
        if (digits.length() == 11 && digits.startsWith("1")) return digits;
        if ((trimmed.startsWith("+") || trimmed.startsWith("00")) && digits.length() >= 8 && digits.length() <= 15) {
            return digits;
        }
        return null;
    }

    /** Primer nombre para el saludo ("Hola Ana"); "cliente" si no hay nombre. */
    public static String firstName(@Nullable String fullName) {
        String name = fullName == null ? "" : fullName.trim();
        if (name.isEmpty()) return "cliente";
        return name.split("\\s+")[0];
    }

    /** "el sábado 15 de noviembre" */
    public static String date(LocalDate date) {
        return "el " + DAYS[date.getDayOfWeek().getValue() - 1] + " " + date.getDayOfMonth() + " de "
                + MONTHS[date.getMonthValue() - 1];
    }

    /** "10:00 a. m." */
    public static String time(LocalTime time) {
        int h = time.getHour() % 12 == 0 ? 12 : time.getHour() % 12;
        return h + ":" + String.format("%02d", time.getMinute()) + (time.getHour() < 12 ? " a. m." : " p. m.");
    }

    /** "el sábado 15 de noviembre a las 10:00 a. m." (sin hora si es de día completo). */
    public static String when(LocalDateTime start, boolean allDay) {
        String day = date(start.toLocalDate());
        return allDay ? day : day + " a las " + time(start.toLocalTime());
    }

    /** Como {@link #when}, pero "hoy …" o "mañana …" si corresponde. */
    public static String whenRelative(LocalDateTime start, boolean allDay, LocalDate today) {
        String text = when(start, allDay).substring("el ".length());
        if (start.toLocalDate().equals(today)) return "hoy " + text;
        if (start.toLocalDate().equals(today.plusDays(1))) return "mañana " + text;
        return "el " + text;
    }

    /** "RD$2,500.00" */
    public static String money(BigDecimal amount) {
        DecimalFormat format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
        return "RD$" + format.format(amount.setScale(2, RoundingMode.HALF_UP));
    }

    /**
     * Meta rechaza variables con saltos de línea, tabuladores o más de 4 espacios seguidos,
     * y vacías. Se dejan en una sola línea y con un largo razonable.
     */
    public static String param(@Nullable String value, String fallback) {
        String clean = value == null ? "" : value.replaceAll("[\\r\\n\\t]+", " ").replaceAll(" {2,}", " ").trim();
        if (clean.isEmpty()) clean = fallback;
        return clean.length() > 200 ? clean.substring(0, 197) + "..." : clean;
    }

    /** Lo que se le ofreció al cliente, desde el título de la orden ("Boda — Ana Pérez" → "Boda"). */
    public static String serviceLabel(@Nullable String orderTitle) {
        if (orderTitle == null) return "Sesión de fotos";
        String first = orderTitle.split("\\s+[—–-]\\s+", 2)[0].trim();
        return first.isEmpty() ? "Sesión de fotos" : first;
    }
}
