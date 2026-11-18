package com.happydev.prestockbackend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.MethodParameter;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.support.DefaultFormattingConversionService;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessTimeTest {

    private static TimeZone original;

    @BeforeAll
    static void useBusinessZone() {
        original = TimeZone.getDefault();
        assertThat(BusinessTime.applyDefaultZone(null)).isEqualTo(ZoneId.of("America/Santo_Domingo"));
    }

    @AfterAll
    static void restoreZone() {
        TimeZone.setDefault(original);
    }

    @Test
    void convierteInstantesConZonaALaHoraLocal() {
        // 9:55 p. m. en RD = 01:55 UTC del día siguiente.
        assertThat(BusinessTime.parseLocalDateTime("2026-11-19T01:55:00.000Z"))
                .isEqualTo(LocalDateTime.of(2026, 11, 18, 21, 55));
        assertThat(BusinessTime.parseLocalDateTime("2026-11-18T21:55:00-04:00"))
                .isEqualTo(LocalDateTime.of(2026, 11, 18, 21, 55));
    }

    @Test
    void dejaTalCualLoQueNoTraeZona() {
        assertThat(BusinessTime.parseLocalDateTime("2026-11-18T10:30:00"))
                .isEqualTo(LocalDateTime.of(2026, 11, 18, 10, 30));
        assertThat(BusinessTime.parseLocalDateTime("2026-11-18"))
                .isEqualTo(LocalDateTime.of(2026, 11, 18, 0, 0));
        assertThat(BusinessTime.parseLocalDateTime(" ")).isNull();
    }

    @SuppressWarnings("unused")
    void filtro(@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate) {
    }

    @Test
    void losParametrosConDateTimeFormatUsanLaZonaDelNegocio() throws Exception {
        DefaultFormattingConversionService conversion = new DefaultFormattingConversionService();
        new WebMvcConfig().addFormatters(conversion);
        MethodParameter param = new MethodParameter(
                BusinessTimeTest.class.getDeclaredMethod("filtro", LocalDateTime.class), 0);

        Object value = conversion.convert("2026-11-18T04:00:00.000Z",
                TypeDescriptor.valueOf(String.class), new TypeDescriptor(param));

        assertThat(value).isEqualTo(LocalDateTime.of(2026, 11, 18, 0, 0));
    }

    record Venta(LocalDateTime saleDate) {
    }

    @Test
    void losCuerposJsonUsanLaZonaDelNegocio() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
                .withUserConfiguration(LocalDateTimeParsingConfig.class)
                .run(context -> {
                    ObjectMapper mapper = context.getBean(ObjectMapper.class);
                    assertThat(mapper.readValue("{\"saleDate\":\"2026-11-19T01:55:00.000Z\"}", Venta.class).saleDate())
                            .isEqualTo(LocalDateTime.of(2026, 11, 18, 21, 55));
                    assertThat(mapper.readValue("{\"saleDate\":\"2026-11-18T10:30:00\"}", Venta.class).saleDate())
                            .isEqualTo(LocalDateTime.of(2026, 11, 18, 10, 30));
                    // La salida no cambia: hora local sin zona.
                    assertThat(mapper.writeValueAsString(new Venta(LocalDateTime.of(2026, 11, 18, 21, 55))))
                            .isEqualTo("{\"saleDate\":\"2026-11-18T21:55:00\"}");
                });
    }
}
