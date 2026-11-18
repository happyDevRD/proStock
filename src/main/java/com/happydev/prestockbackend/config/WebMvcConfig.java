package com.happydev.prestockbackend.config;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /** Filtros de fechas con zona ("...Z") → hora local del negocio. Ver LocalDateTimeParsingConfig. */
    @Override
    public void addFormatters(@NonNull FormatterRegistry registry) {
        registry.addFormatterForFieldAnnotation(LocalDateTimeParsingConfig.dateTimeFormatFactory());
    }

    @Value("${storage.location}") //Obten la ruta
    private String storageLocation;

    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**") // La URL para acceder a los archivos
                .addResourceLocations("file:" + storageLocation + "/") // La ruta física en el sistema de archivos
                .setCachePeriod(0); // Desactiva la caché en desarrollo.  En producción, configúrala.
    }
}
