package com.happydev.prestockbackend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Lista blanca para el rol PRODUCCION (fotógrafos y editores): solo puede usar
 * "Mis trabajos" y lo mínimo para cargar la aplicación.
 *
 * <p>Hace falta porque muchos endpoints de lectura solo exigen estar autenticado
 * (sin permiso específico); sin este filtro un fotógrafo podría leer ventas o
 * clientes llamando a la API directamente.
 */
public class ProductionRoleAccessFilter extends OncePerRequestFilter {

    static final String ROLE = "ROLE_PRODUCCION";

    private record Rule(HttpMethod method, String prefix) {
        boolean matches(HttpServletRequest req, String path) {
            return (method == null || method.matches(req.getMethod())) && path.startsWith(prefix);
        }
    }

    private static final List<Rule> ALLOWED = List.of(
            new Rule(null, "/api/auth/"),
            new Rule(null, "/api/my-work"),
            new Rule(HttpMethod.GET, "/api/features"),
            new Rule(HttpMethod.GET, "/api/modules/flags"),
            new Rule(HttpMethod.GET, "/api/company-config"),
            new Rule(HttpMethod.GET, "/api/portal/company-info")
    );

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !path(request).startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean production = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> ROLE.equals(a.getAuthority()));
        String path = path(request);
        if (production && ALLOWED.stream().noneMatch(r -> r.matches(request, path))
                && !HttpMethod.OPTIONS.matches(request.getMethod())) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(
                    "{\"code\":\"FORBIDDEN_FOR_ROLE\",\"message\":\"Tu usuario solo tiene acceso a Mis trabajos.\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private static String path(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String ctx = request.getContextPath();
        return ctx != null && !ctx.isEmpty() && uri.startsWith(ctx) ? uri.substring(ctx.length()) : uri;
    }
}
