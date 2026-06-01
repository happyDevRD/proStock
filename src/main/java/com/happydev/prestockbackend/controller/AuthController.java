package com.happydev.prestockbackend.controller;

import com.happydev.prestockbackend.dto.LoginRequest;
import com.happydev.prestockbackend.entity.User;
import com.happydev.prestockbackend.entity.UserRole;
import com.happydev.prestockbackend.repository.UserRepository;
import com.happydev.prestockbackend.security.AuthCookieSupport;
import com.happydev.prestockbackend.security.JwtService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final AuthCookieSupport authCookieSupport;

    public AuthController(
            UserRepository userRepository,
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            AuthCookieSupport authCookieSupport
    ) {
        this.userRepository = userRepository;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.authCookieSupport = authCookieSupport;
    }

    @PostMapping("/login")
    public LoginResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response
    ) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username().trim(), request.password())
        );
        String token = jwtService.generateToken(authentication);
        authCookieSupport.writeAccessToken(response, token);
        AuthMeResponse me = toMeResponse(loadUser(authentication.getName()));
        return new LoginResponse(
                me.username(),
                me.email(),
                me.firstName(),
                me.lastName(),
                me.role(),
                token
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
        return toMeResponse(loadUser(principal.getName()));
    }

    private User loadUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no encontrado"));
    }

    private AuthMeResponse toMeResponse(User user) {
        return new AuthMeResponse(
                user.getUsername(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getRole() != null ? user.getRole().name() : UserRole.USER.name()
        );
    }

    public record AuthMeResponse(
            String username,
            String email,
            String firstName,
            String lastName,
            String role
    ) {}

    /** Incluye JWT para clientes SPA (p. ej. frontend en otro puerto que la cookie HttpOnly). */
    public record LoginResponse(
            String username,
            String email,
            String firstName,
            String lastName,
            String role,
            String accessToken
    ) {}
}
