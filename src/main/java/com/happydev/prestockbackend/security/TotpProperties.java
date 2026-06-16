package com.happydev.prestockbackend.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.totp")
public class TotpProperties {

    /** Nombre mostrado en la app del autenticador (Google Authenticator, Authy, etc.). */
    private String issuer = "ProStock";

    /** Si es true, los usuarios con rol ADMIN deben configurar 2FA (login sigue permitido, pero se exige setup). */
    private boolean enforceAdmin = false;

    /** Clave (hex) usada para cifrar el secreto TOTP en reposo. Debe configurarse en producción. */
    private String encryptionKey = "0000000000000000000000000000000000000000000000000000000000aa";

    /** Salt (hex) para el cifrado del secreto TOTP. Debe configurarse en producción. */
    private String encryptionSalt = "deadbeef";

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public boolean isEnforceAdmin() {
        return enforceAdmin;
    }

    public void setEnforceAdmin(boolean enforceAdmin) {
        this.enforceAdmin = enforceAdmin;
    }

    public String getEncryptionKey() {
        return encryptionKey;
    }

    public void setEncryptionKey(String encryptionKey) {
        this.encryptionKey = encryptionKey;
    }

    public String getEncryptionSalt() {
        return encryptionSalt;
    }

    public void setEncryptionSalt(String encryptionSalt) {
        this.encryptionSalt = encryptionSalt;
    }
}
