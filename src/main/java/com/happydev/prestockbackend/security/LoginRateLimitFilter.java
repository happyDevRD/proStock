package com.happydev.prestockbackend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.happydev.prestockbackend.exception.ErrorDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/** Fase Q3: limita los intentos de login por dirección IP en una ventana deslizante. */
@Component
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final String LOGIN_PATH = "/api/auth/login";

    private final LoginSecurityProperties properties;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, Deque<Long>> attemptsByIp = new ConcurrentHashMap<>();

    public LoginRateLimitFilter(LoginSecurityProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        if (!HttpMethod.POST.matches(request.getMethod()) || !request.getRequestURI().endsWith(LOGIN_PATH)) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = resolveClientIp(request);
        long now = System.currentTimeMillis();
        long windowMillis = properties.getRateLimit().getWindowSeconds() * 1000L;
        int maxRequests = properties.getRateLimit().getMaxRequests();

        Deque<Long> timestamps = attemptsByIp.computeIfAbsent(clientIp, ip -> new ConcurrentLinkedDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && now - timestamps.peekFirst() > windowMillis) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= maxRequests) {
                respondTooManyRequests(response, request);
                return;
            }
            timestamps.addLast(now);
        }

        filterChain.doFilter(request, response);
    }

    private void respondTooManyRequests(HttpServletResponse response, HttpServletRequest request) throws IOException {
        ErrorDetails errorDetails = new ErrorDetails(
                LocalDateTime.now(),
                "LOGIN_RATE_LIMITED",
                "Demasiados intentos de inicio de sesión. Espera un momento e intenta de nuevo.",
                "uri=" + request.getRequestURI()
        );
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), errorDetails);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
