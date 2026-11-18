package com.happydev.prestockbackend.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.AnnotationFormatterFactory;
import org.springframework.format.Parser;
import org.springframework.format.Printer;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.lang.NonNull;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;

/**
 * El navegador manda instantes con zona (toISOString() → "...Z"). Por defecto
 * Spring y Jackson los convierten a LocalDateTime descartando la zona, lo que
 * deja la hora en UTC. Aquí se convierten a la hora local del negocio, tanto en
 * cuerpos JSON como en parámetros con @DateTimeFormat. Los valores sin zona se
 * siguen leyendo tal cual.
 */
@Configuration
public class LocalDateTimeParsingConfig {

    /** Cuerpos JSON: se registra después del JavaTimeModule, así que tiene prioridad. */
    @Bean
    public Module zoneAwareLocalDateTimeModule() {
        SimpleModule module = new SimpleModule("ZoneAwareLocalDateTime");
        module.addDeserializer(LocalDateTime.class, new ZoneAwareLocalDateTimeDeserializer());
        return module;
    }

    static final class ZoneAwareLocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {
        @Override
        public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            if (p.hasToken(JsonToken.VALUE_STRING)) {
                try {
                    return BusinessTime.parseLocalDateTime(p.getText());
                } catch (RuntimeException e) {
                    return (LocalDateTime) ctxt.handleWeirdStringValue(LocalDateTime.class, p.getText(), e.getMessage());
                }
            }
            // Arreglos [2026,11,18,10,0] y demás formatos: el deserializador estándar.
            return LocalDateTimeDeserializer.INSTANCE.deserialize(p, ctxt);
        }
    }

    /** Parámetros de URL con @DateTimeFormat (filtros de fechas). */
    static final class ZoneAwareDateTimeFormatFactory implements AnnotationFormatterFactory<DateTimeFormat> {
        @Override
        @NonNull
        public Set<Class<?>> getFieldTypes() {
            return Set.of(LocalDateTime.class);
        }

        @Override
        @NonNull
        public Printer<?> getPrinter(@NonNull DateTimeFormat annotation, @NonNull Class<?> fieldType) {
            return (Printer<LocalDateTime>) (value, locale) -> DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(value);
        }

        @Override
        @NonNull
        public Parser<?> getParser(@NonNull DateTimeFormat annotation, @NonNull Class<?> fieldType) {
            return (Parser<LocalDateTime>) (text, locale) -> BusinessTime.parseLocalDateTime(text);
        }
    }

    static AnnotationFormatterFactory<DateTimeFormat> dateTimeFormatFactory() {
        return new ZoneAwareDateTimeFormatFactory();
    }
}
