package com.happydev.prestockbackend.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.cookie")
public class CookieProperties {

    private boolean secure = false;

    /**
     * Lax para mismo sitio o proxy dev; None en producción cross-origin (requiere secure=true).
     */
    private String sameSite = "Lax";

    public boolean isSecure() {
        return secure;
    }

    public void setSecure(boolean secure) {
        this.secure = secure;
    }

    public String getSameSite() {
        return sameSite;
    }

    public void setSameSite(String sameSite) {
        this.sameSite = sameSite;
    }
}
