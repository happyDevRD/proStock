package com.happydev.prestockbackend.calendar;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Prueba contra Google de verdad (se salta si no hay llave):
 * {@code GOOGLE_CALENDAR_SA_FILE=/ruta/llave.json ./gradlew test --tests '*GoogleCalendarLiveTest'}.
 * Usa el calendario propio de la cuenta de servicio (su correo), o {@code GOOGLE_CALENDAR_ID} si se da.
 */
class GoogleCalendarLiveTest {

    @Test
    void creaMueveYBorraUnEventoEnGoogle() throws Exception {
        String file = System.getenv("GOOGLE_CALENDAR_SA_FILE");
        assumeTrue(file != null && Files.exists(Path.of(file)), "sin llave: prueba en vivo omitida");
        String json = Files.readString(Path.of(file));
        GoogleCalendarClient client = new GoogleCalendarClient(new ObjectMapper());
        String calendar = System.getenv().getOrDefault("GOOGLE_CALENDAR_ID", client.parse(json).clientEmail());
        String eventId = "prostocktest" + System.currentTimeMillis();

        assertNotNull(client.calendarSummary(json, calendar));

        LocalDateTime start = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0);
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("summary", "Prueba ProStock (se borra sola)");
        GoogleCalendarSync.putTimes(event, start, start.plusHours(1), false);
        client.upsert(json, calendar, eventId, event); // no existe → se crea con ese id

        event.put("summary", "Prueba ProStock movida");
        GoogleCalendarSync.putTimes(event, start.plusHours(2), start.plusHours(3), false);
        client.upsert(json, calendar, eventId, event); // ya existe → se actualiza

        client.delete(json, calendar, eventId);
        client.delete(json, calendar, eventId); // borrar dos veces no es error

        // Volver a agendar una orden cancelada reutiliza el mismo id (evento borrado → se reactiva).
        client.upsert(json, calendar, eventId, event);
        client.delete(json, calendar, eventId);
    }
}
