package com.happydev.prestockbackend.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Fase Q3: política de contraseñas configurable vía variables de entorno. */
@ConfigurationProperties(prefix = "app.security.password")
public class PasswordPolicyProperties {

    /** Longitud mínima (default 8). */
    private int minLength = 8;

    /** Exigir al menos una mayúscula. */
    private boolean requireUppercase = false;

    /** Exigir al menos una minúscula. */
    private boolean requireLowercase = false;

    /** Exigir al menos un dígito. */
    private boolean requireDigit = false;

    /** Exigir al menos un carácter especial. */
    private boolean requireSpecial = false;

    /** Días antes de que la contraseña expire (0 = nunca). */
    private int maxAgeDays = 0;

    public int getMinLength() { return minLength; }
    public void setMinLength(int minLength) { this.minLength = minLength; }

    public boolean isRequireUppercase() { return requireUppercase; }
    public void setRequireUppercase(boolean requireUppercase) { this.requireUppercase = requireUppercase; }

    public boolean isRequireLowercase() { return requireLowercase; }
    public void setRequireLowercase(boolean requireLowercase) { this.requireLowercase = requireLowercase; }

    public boolean isRequireDigit() { return requireDigit; }
    public void setRequireDigit(boolean requireDigit) { this.requireDigit = requireDigit; }

    public boolean isRequireSpecial() { return requireSpecial; }
    public void setRequireSpecial(boolean requireSpecial) { this.requireSpecial = requireSpecial; }

    public int getMaxAgeDays() { return maxAgeDays; }
    public void setMaxAgeDays(int maxAgeDays) { this.maxAgeDays = maxAgeDays; }
}
