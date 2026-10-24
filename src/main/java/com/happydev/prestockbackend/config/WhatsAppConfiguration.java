package com.happydev.prestockbackend.config;

import com.happydev.prestockbackend.whatsapp.WhatsAppProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(WhatsAppProperties.class)
public class WhatsAppConfiguration {
}
