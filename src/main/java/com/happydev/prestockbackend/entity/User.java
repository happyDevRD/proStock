package com.happydev.prestockbackend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "users")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(min = 3, max = 50)
    @Column(unique = true, nullable = false)
    private String username; // Nombre de usuario (único)

    @NotBlank
    @Size(min = 6) // Longitud mínima de la contraseña (ajusta según tus requisitos)
    @Column(nullable = false)
    private String password; // Contraseña (debe estar encriptada, ver Spring Security)

    @NotBlank
    @Email
    @Column(unique = true, nullable = false)
    private String email;

    @Column(name = "first_name")
    private String firstName; // Nombre

    @Column(name = "last_name")
    private String lastName;  // Apellido

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private UserRole role = UserRole.USER;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts = 0;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    /** Secreto TOTP cifrado (ver {@code TotpService}); null si 2FA nunca se configuró. */
    @Column(name = "totp_secret")
    private String totpSecret;

    @Column(name = "totp_enabled", nullable = false)
    private boolean totpEnabled = false;

    /** Marca cuándo se cambió la contraseña por última vez (para política de caducidad). */
    @Column(name = "password_changed_at")
    private LocalDateTime passwordChangedAt;

    //Relacion con StockMovement
    @OneToMany(mappedBy = "user") //Un usuario puede realizar muchos movimientos
    private List<StockMovement> stockMovements;
}