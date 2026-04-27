package com.happydev.prestockbackend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Pattern;
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

    @Column(name = "rnc_cedula", length = 11)
    @Pattern(regexp = "^\\d{9}(\\d{2})?$", message = "RNC/Cedula debe tener 9 u 11 digitos")
    private String rncCedula;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_identificacion", length = 30)
    private TipoIdentificacion tipoIdentificacion;
}

