package com.happydev.prestockbackend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "product_images")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductImage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "file_name", nullable = false) // Cambiamos 'url' por 'file_name'
    private String fileName; // Solo el nombre del archivo (y ruta relativa, si es necesario)

    // Podrías agregar un campo para el tipo de archivo (MIME type)
    @Column(name = "content_type")
    private String contentType;
}