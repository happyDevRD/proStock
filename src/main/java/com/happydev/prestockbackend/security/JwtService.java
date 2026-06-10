package com.happydev.prestockbackend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
public class JwtService {

    private final JwtProperties jwtProperties;
    private final SecretKey signingKey;

    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        byte[] keyBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("app.security.jwt.secret debe tener al menos 32 caracteres");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(Authentication authentication) {
        Instant now = Instant.now();
        Instant expiry = now.plus(jwtProperties.getExpirationHours(), ChronoUnit.HOURS);
        List<String> authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        String role = authorities.stream().findFirst().orElse("ROLE_USER");

        return Jwts.builder()
                .subject(authentication.getName())
                .claim("role", role)
                .claim("authorities", authorities)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    public Optional<JwtUserPrincipal> parseToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            String username = claims.getSubject();
            if (username == null || username.isBlank()) {
                return Optional.empty();
            }
            List<?> rawAuthorities = claims.get("authorities", List.class);
            List<String> authorities;
            if (rawAuthorities != null && !rawAuthorities.isEmpty()) {
                authorities = rawAuthorities.stream().map(String::valueOf).toList();
            } else {
                // Fallback para JWTs emitidos antes de agregar el claim "authorities"
                String role = claims.get("role", String.class);
                authorities = List.of(role == null || role.isBlank() ? "ROLE_USER" : role);
            }
            return Optional.of(new JwtUserPrincipal(username, authorities));
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public record JwtUserPrincipal(String username, List<String> authorities) {}
}
