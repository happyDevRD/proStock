package com.happydev.prestockbackend.entity;

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

    //Relación con productos
    @OneToMany(mappedBy = "supplier", cascade = CascadeType.ALL)
    private List<Product> products; //Lista de productos
}
