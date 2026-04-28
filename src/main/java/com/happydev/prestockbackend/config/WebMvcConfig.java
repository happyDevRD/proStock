package com.happydev.prestockbackend.config;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${storage.location}") //Obten la ruta
    private String storageLocation;

    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**") // La URL para acceder a los archivos
                .addResourceLocations("file:" + storageLocation + "/") // La ruta física en el sistema de archivos
                .setCachePeriod(0); // Desactiva la caché en desarrollo.  En producción, configúrala.
    }
}
