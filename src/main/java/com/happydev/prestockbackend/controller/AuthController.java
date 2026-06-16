package com.happydev.prestockbackend.controller;

import com.happydev.prestockbackend.dto.LoginRequest;
import com.happydev.prestockbackend.entity.User;
import com.happydev.prestockbackend.entity.UserRole;
import com.happydev.prestockbackend.repository.UserRepository;
import com.happydev.prestockbackend.security.AuthCookieSupport;
import com.happydev.prestockbackend.security.InvalidTotpException;
import com.happydev.prestockbackend.security.JwtService;
import com.happydev.prestockbackend.security.LoginSecurityProperties;
import com.happydev.prestockbackend.security.TotpProperties;
import com.happydev.prestockbackend.security.TotpRequiredException;
import com.happydev.prestockbackend.security.TotpService;
import com.happydev.prestockbackend.service.PermissionService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final AuthCookieSupport authCookieSupport;
    private final PermissionService permissionService;
    private final LoginSecurityProperties loginSecurityProperties;
    private final TotpService totpService;
    private final TotpProperties totpProperties;

    public AuthController(
            UserRepository userRepository,
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            AuthCookieSupport authCookieSupport,
            PermissionService permissionService,
            LoginSecurityProperties loginSecurityProperties,
            TotpService totpService,
            TotpProperties totpProperties
    ) {
        this.userRepository = userRepository;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.authCookieSupport = authCookieSupport;
        this.permissionService = permissionService;
        this.loginSecurityProperties = loginSecurityProperties;
        this.totpService = totpService;
        this.totpProperties = totpProperties;
    }

    @PostMapping("/login")
    public LoginResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response
    ) {
        String username = request.username().trim();
        Optional<User> existingUser = userRepository.findByUsername(username);
        existingUser.ifPresent(this::rejectIfLocked);

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, request.password())
            );
        } catch (AuthenticationException ex) {
            existingUser.ifPresent(this::registerFailedAttempt);
            throw ex;
        }

        User user = existingUser.orElseGet(() -> loadUser(authentication.getName()));
        if (user.isTotpEnabled()) {
            verifyTotpOrFail(user, request.totpCode());
        }
        resetFailedAttempts(user);
        List<String> permissions = permissionService.getEffectivePermissions(user);

        List<GrantedAuthority> enrichedAuthorities = new ArrayList<>(authentication.getAuthorities());
        permissions.forEach(code -> enrichedAuthorities.add(new SimpleGrantedAuthority(code)));
        Authentication enriched = new UsernamePasswordAuthenticationToken(
                authentication.getName(), null, enrichedAuthorities
        );

        String token = jwtService.generateToken(enriched);
        authCookieSupport.writeAccessToken(response, token);
        AuthMeResponse me = toMeResponse(user, permissions);
        boolean totpSetupRequired = totpProperties.isEnforceAdmin()
                && user.getRole() == UserRole.ADMIN
                && !user.isTotpEnabled();
        return new LoginResponse(
                me.username(),
                me.email(),
                me.firstName(),
                me.lastName(),
                me.role(),
                me.permissions(),
                token,
                totpSetupRequired
        );
    }

    @PostMapping("/logout")
    public void logout(HttpServletResponse response) {
        authCookieSupport.clearAccessToken(response);
    }

    @GetMapping("/me")
    public AuthMeResponse me(Principal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesión no válida");
        }
        User user = loadUser(principal.getName());
        return toMeResponse(user, permissionService.getEffectivePermissions(user));
    }

    private User loadUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no encontrado"));
    }

    /** Rechaza el login si la cuenta está temporalmente bloqueada por intentos fallidos previos. */
    private void rejectIfLocked(User user) {
        LocalDateTime lockedUntil = user.getLockedUntil();
        if (lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now())) {
            long minutes = Math.max(1, Duration.between(LocalDateTime.now(), lockedUntil).toMinutes());
            throw new LockedException(
                    "Cuenta bloqueada temporalmente por demasiados intentos fallidos. Intenta de nuevo en "
                            + minutes + " minuto" + (minutes == 1 ? "" : "s") + "."
            );
        }
    }

    /** Valida el código TOTP para usuarios con 2FA activo; un código inválido cuenta como intento fallido. */
    private void verifyTotpOrFail(User user, String totpCode) {
        if (totpCode == null || totpCode.isBlank()) {
            throw new TotpRequiredException("Se requiere el código de autenticación de dos factores.");
        }
        String secret = totpService.decrypt(user.getTotpSecret());
        if (!totpService.verifyCode(secret, totpCode)) {
            registerFailedAttempt(user);
            throw new InvalidTotpException("Código de autenticación inválido.");
        }
    }

    /** Incrementa el contador de intentos fallidos y bloquea la cuenta si alcanza el máximo configurado. */
    private void registerFailedAttempt(User user) {
        int attempts = user.getFailedLoginAttempts() + 1;
        if (attempts >= loginSecurityProperties.getMaxAttempts()) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(LocalDateTime.now().plusMinutes(loginSecurityProperties.getLockoutMinutes()));
        } else {
            user.setFailedLoginAttempts(attempts);
        }
        userRepository.save(user);
    }

    /** Limpia el contador de intentos fallidos y el bloqueo tras un login exitoso. */
    private void resetFailedAttempts(User user) {
        if (user.getFailedLoginAttempts() != 0 || user.getLockedUntil() != null) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            userRepository.save(user);
        }
    }

    private AuthMeResponse toMeResponse(User user, List<String> permissions) {
        return new AuthMeResponse(
                user.getUsername(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getRole() != null ? user.getRole().name() : UserRole.USER.name(),
                permissions
        );
    }

    public record AuthMeResponse(
            String username,
            String email,
            String firstName,
            String lastName,
            String role,
            List<String> permissions
    ) {}

    /** Incluye JWT para clientes SPA (p. ej. frontend en otro puerto que la cookie HttpOnly). */
    public record LoginResponse(
            String username,
            String email,
            String firstName,
            String lastName,
            String role,
            List<String> permissions,
            String accessToken,
            boolean totpSetupRequired
    ) {}
}
