package com.happydev.prestockbackend.security;

import com.happydev.prestockbackend.entity.User;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Fase Q3: validación de fortaleza y caducidad de contraseñas. */
@Service
public class PasswordPolicyService {

    private final PasswordPolicyProperties policy;

    public PasswordPolicyService(PasswordPolicyProperties policy) {
        this.policy = policy;
    }

    /**
     * Valida que la contraseña cumpla la política configurada.
     * Lanza {@link PasswordPolicyViolationException} con todos los errores encontrados.
     */
    public void validate(String rawPassword) {
        if (rawPassword == null) {
            throw new PasswordPolicyViolationException("La contraseña no puede ser nula.");
        }

        List<String> errors = new ArrayList<>();

        if (rawPassword.length() < policy.getMinLength()) {
            errors.add("debe tener al menos " + policy.getMinLength() + " caracteres");
        }
        if (policy.isRequireUppercase() && !rawPassword.chars().anyMatch(Character::isUpperCase)) {
            errors.add("debe incluir al menos una letra mayúscula");
        }
        if (policy.isRequireLowercase() && !rawPassword.chars().anyMatch(Character::isLowerCase)) {
            errors.add("debe incluir al menos una letra minúscula");
        }
        if (policy.isRequireDigit() && !rawPassword.chars().anyMatch(Character::isDigit)) {
            errors.add("debe incluir al menos un dígito");
        }
        if (policy.isRequireSpecial() && rawPassword.chars().allMatch(c -> Character.isLetterOrDigit(c) || Character.isWhitespace(c))) {
            errors.add("debe incluir al menos un carácter especial (!@#$%^&*…)");
        }

        if (!errors.isEmpty()) {
            throw new PasswordPolicyViolationException("La contraseña " + String.join(", ", errors) + ".");
        }
    }

    /** Devuelve {@code true} si la contraseña del usuario ha caducado según {@code maxAgeDays}. */
    public boolean isExpired(User user) {
        int maxAge = policy.getMaxAgeDays();
        if (maxAge <= 0) {
            return false;
        }
        LocalDateTime changedAt = user.getPasswordChangedAt();
        if (changedAt == null) {
            return true; // nunca se registró; considerar caducada
        }
        return changedAt.plusDays(maxAge).isBefore(LocalDateTime.now());
    }
}
