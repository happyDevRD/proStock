package com.happydev.prestockbackend.controller;

import com.happydev.prestockbackend.dto.TotpCodeRequest;
import com.happydev.prestockbackend.dto.TotpSetupResponse;
import com.happydev.prestockbackend.dto.TotpStatusResponse;
import com.happydev.prestockbackend.entity.User;
import com.happydev.prestockbackend.entity.UserRole;
import com.happydev.prestockbackend.repository.UserRepository;
import com.happydev.prestockbackend.security.InvalidTotpException;
import com.happydev.prestockbackend.security.TotpProperties;
import com.happydev.prestockbackend.security.TotpService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;

@RestController
@RequestMapping("/api/auth/2fa")
public class TwoFactorController {

    private final UserRepository userRepository;
    private final TotpService totpService;
    private final TotpProperties totpProperties;

    public TwoFactorController(UserRepository userRepository, TotpService totpService, TotpProperties totpProperties) {
        this.userRepository = userRepository;
        this.totpService = totpService;
        this.totpProperties = totpProperties;
    }

    @GetMapping("/status")
    public TotpStatusResponse status(Principal principal) {
        User user = loadUser(principal);
        boolean enforced = totpProperties.isEnforceAdmin() && user.getRole() == UserRole.ADMIN;
        return new TotpStatusResponse(user.isTotpEnabled(), enforced);
    }

    /** Genera un nuevo secreto (pendiente, aún no habilitado) y lo persiste cifrado. */
    @PostMapping("/setup")
    public TotpSetupResponse setup(Principal principal) {
        User user = loadUser(principal);
        String secret = totpService.generateSecret();
        user.setTotpSecret(totpService.encrypt(secret));
        user.setTotpEnabled(false);
        userRepository.save(user);

        String qrCodeDataUri = totpService.buildQrDataUri(user.getUsername(), secret);
        return new TotpSetupResponse(secret, qrCodeDataUri);
    }

    /** Verifica el código contra el secreto pendiente y activa 2FA. */
    @PostMapping("/enable")
    public TotpStatusResponse enable(@Valid @RequestBody TotpCodeRequest request, Principal principal) {
        User user = loadUser(principal);
        if (user.getTotpSecret() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Primero debes generar un secreto de 2FA.");
        }
        String secret = totpService.decrypt(user.getTotpSecret());
        if (!totpService.verifyCode(secret, request.code())) {
            throw new InvalidTotpException("Código de autenticación inválido.");
        }
        user.setTotpEnabled(true);
        userRepository.save(user);

        boolean enforced = totpProperties.isEnforceAdmin() && user.getRole() == UserRole.ADMIN;
        return new TotpStatusResponse(true, enforced);
    }

    /** Verifica el código TOTP actual y desactiva 2FA, eliminando el secreto. */
    @PostMapping("/disable")
    public TotpStatusResponse disable(@Valid @RequestBody TotpCodeRequest request, Principal principal) {
        User user = loadUser(principal);
        if (!user.isTotpEnabled() || user.getTotpSecret() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "2FA no está habilitado para este usuario.");
        }
        String secret = totpService.decrypt(user.getTotpSecret());
        if (!totpService.verifyCode(secret, request.code())) {
            throw new InvalidTotpException("Código de autenticación inválido.");
        }
        user.setTotpEnabled(false);
        user.setTotpSecret(null);
        userRepository.save(user);

        boolean enforced = totpProperties.isEnforceAdmin() && user.getRole() == UserRole.ADMIN;
        return new TotpStatusResponse(false, enforced);
    }

    private User loadUser(Principal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesión no válida");
        }
        return userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no encontrado"));
    }
}
