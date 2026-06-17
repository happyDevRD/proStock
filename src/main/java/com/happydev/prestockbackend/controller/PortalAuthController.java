package com.happydev.prestockbackend.controller;

import com.happydev.prestockbackend.entity.Customer;
import com.happydev.prestockbackend.repository.CustomerRepository;
import com.happydev.prestockbackend.security.JwtService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/portal/auth")
public class PortalAuthController {

    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public PortalAuthController(
            CustomerRepository customerRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.customerRepository = customerRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public PortalLoginResponse login(@Valid @RequestBody PortalLoginRequest request) {
        Customer customer = customerRepository.findByEmailAndPortalEnabledTrue(request.email())
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas."));

        if (customer.getPortalPassword() == null
                || !passwordEncoder.matches(request.password(), customer.getPortalPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas.");
        }

        customer.setPortalLastLogin(LocalDateTime.now());
        customerRepository.save(customer);

        String token = jwtService.generatePortalToken(customer.getId());
        return new PortalLoginResponse(
                customer.getId(),
                customer.getFirstName(),
                customer.getLastName(),
                customer.getEmail(),
                token
        );
    }

    public record PortalLoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password
    ) {}

    public record PortalLoginResponse(
            Long customerId,
            String firstName,
            String lastName,
            String email,
            String accessToken
    ) {}
}
