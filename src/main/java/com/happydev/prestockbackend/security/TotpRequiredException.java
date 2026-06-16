package com.happydev.prestockbackend.security;

import org.springframework.security.core.AuthenticationException;

/** Las credenciales son válidas pero el usuario tiene 2FA activo y no envió el código TOTP. */
public class TotpRequiredException extends AuthenticationException {
    public TotpRequiredException(String msg) {
        super(msg);
    }
}
