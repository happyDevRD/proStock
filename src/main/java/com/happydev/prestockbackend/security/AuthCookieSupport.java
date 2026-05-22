package com.happydev.prestockbackend.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class AuthCookieSupport {

    private final JwtProperties jwtProperties;
    private final CookieProperties cookieProperties;

    public AuthCookieSupport(JwtProperties jwtProperties, CookieProperties cookieProperties) {
        this.jwtProperties = jwtProperties;
        this.cookieProperties = cookieProperties;
    }

    public void writeAccessToken(HttpServletResponse response, String token) {
        ResponseCookie cookie = baseCookieBuilder(token)
                .maxAge(Duration.ofHours(jwtProperties.getExpirationHours()))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public void clearAccessToken(HttpServletResponse response) {
        ResponseCookie cookie = baseCookieBuilder("")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private ResponseCookie.ResponseCookieBuilder baseCookieBuilder(String value) {
        return ResponseCookie.from(jwtProperties.getCookieName(), value)
                .httpOnly(true)
                .secure(cookieProperties.isSecure())
                .sameSite(cookieProperties.getSameSite())
                .path("/");
    }
}
