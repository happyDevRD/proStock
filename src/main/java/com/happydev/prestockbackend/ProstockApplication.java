package com.happydev.prestockbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // Habilita el scheduling
public class ProstockApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProstockApplication.class, args);
    }

}
