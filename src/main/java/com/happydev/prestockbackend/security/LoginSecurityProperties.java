package com.happydev.prestockbackend.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.login")
public class LoginSecurityProperties {

    /** Intentos fallidos permitidos antes de bloquear la cuenta temporalmente. */
    private int maxAttempts = 5;

    /** Duración del bloqueo de cuenta tras superar {@code maxAttempts}. */
    private int lockoutMinutes = 15;

    private final RateLimit rateLimit = new RateLimit();

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public int getLockoutMinutes() {
        return lockoutMinutes;
    }

    public void setLockoutMinutes(int lockoutMinutes) {
        this.lockoutMinutes = lockoutMinutes;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public static class RateLimit {
        /** Máximo de intentos de login por IP dentro de la ventana. */
        private int maxRequests = 10;

        /** Duración de la ventana deslizante, en segundos. */
        private int windowSeconds = 60;

        public int getMaxRequests() {
            return maxRequests;
        }

        public void setMaxRequests(int maxRequests) {
            this.maxRequests = maxRequests;
        }

        public int getWindowSeconds() {
            return windowSeconds;
        }

        public void setWindowSeconds(int windowSeconds) {
            this.windowSeconds = windowSeconds;
        }
    }
}
