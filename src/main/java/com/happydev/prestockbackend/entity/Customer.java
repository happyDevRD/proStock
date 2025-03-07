package com.happydev.prestockbackend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "customers")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Customer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(unique = true) // Asegura que el correo electrónico sea único
    private String email;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column
    private String address; // Podrías tener una entidad Address separada

    // ... otros campos, como fecha de registro, etc. ...
}

