package com.happydev.prestockbackend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Entity
@Table(name = "suppliers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Supplier {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column
    private String contactName; // Nombre de contacto

    @Column
    private String contactEmail;

    @Column
    private String phone;
    @Column
    private String address;

    @Column(name = "rnc_cedula", length = 20)
    private String rncCedula;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_identificacion", length = 30)
    private TipoIdentificacion tipoIdentificacion;

    // Relación con productos (no serializar en JSON: evita grafo infinito supplier → product → category → products → …)
    @JsonIgnore
    @OneToMany(mappedBy = "supplier", cascade = CascadeType.ALL)
    private List<Product> products;
}
    