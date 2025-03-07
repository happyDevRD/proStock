package com.happydev.prestockbackend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

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

    //  ... otros campos (rol, activo/inactivo, etc.) ...

    //Relacion con StockMovement
    @OneToMany(mappedBy = "user") //Un usuario puede realizar muchos movimientos
    private List<StockMovement> stockMovements;
}