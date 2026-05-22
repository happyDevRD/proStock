package com.happydev.prestockbackend.config;

import com.happydev.prestockbackend.security.CookieProperties;
import com.happydev.prestockbackend.security.JwtProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({JwtProperties.class, CookieProperties.class})
public class JwtConfiguration {
}
