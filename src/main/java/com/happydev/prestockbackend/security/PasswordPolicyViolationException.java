package com.happydev.prestockbackend.security;

/** Fase Q3: contraseña que no cumple la política configurada. */
public class PasswordPolicyViolationException extends RuntimeException {
    public PasswordPolicyViolationException(String message) {
        super(message);
    }
}
