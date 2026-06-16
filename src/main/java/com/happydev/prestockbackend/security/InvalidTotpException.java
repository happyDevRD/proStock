package com.happydev.prestockbackend.security;

import org.springframework.security.core.AuthenticationException;

/** El código TOTP enviado no es válido para el secreto del usuario. */
public class InvalidTotpException extends AuthenticationException {
    public InvalidTotpException(String msg) {
        super(msg);
    }
}
