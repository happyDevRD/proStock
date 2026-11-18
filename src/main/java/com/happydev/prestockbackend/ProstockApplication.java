package com.happydev.prestockbackend;

import com.happydev.prestockbackend.config.BusinessTime;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // Habilita el scheduling
public class ProstockApplication {

    public static void main(String[] args) {
        // Antes de todo: Hibernate, el pool JDBC y LocalDateTime.now() toman la zona por defecto.
        BusinessTime.applyDefaultZone(System.getenv(BusinessTime.ZONE_ENV));
        SpringApplication.run(ProstockApplication.class, args);
    }

}
