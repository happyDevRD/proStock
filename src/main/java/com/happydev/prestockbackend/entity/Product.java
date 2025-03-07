package com.happydev.prestockbackend.entity;



import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Entity
@Table(name = "products")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    @NotBlank
    @Size(min = 3, max = 50)
    private String sku;

    @Column(nullable = false)
    @NotBlank
    @Size(min = 2, max = 255)
    private String name;

    @Column(length = 1000)
    @Size(max = 1000)
    private String description;

    @NotNull
    @ManyToOne(optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @NotNull
    @ManyToOne(optional = false)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Column(nullable = false)
    @NotNull
    @Positive
    private BigDecimal costPrice;

    @Column(nullable = false)
    @NotNull
    @Positive
    private BigDecimal sellingPrice;

    @Column(nullable = false)
    @NotNull
    @PositiveOrZero
    private Integer stock;

    @Column(nullable = false)
    @NotNull
    @PositiveOrZero
    private Integer minStock;

    @Future
    private LocalDate expirationDate; //Opcional

    @NotNull
    private Boolean forSale = false; //Por default false

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductImage> images;


    // Nuevos atributos
    @Column(name = "unit_of_measure", nullable = false)
    @NotBlank // O podrías usar un Enum si tienes un conjunto fijo de unidades
    private String unitOfMeasure;  // kg, litros, unidades, piezas, metros, etc.

    @Column(name = "location") // Podría ser más específico (rack, shelf, bin...)
    private String location;  // Estante, pasillo, bodega, etc.

    @Column(name = "weight")
    @Positive
    private Double weight; // Peso (en kg, libras, etc.)

    @Column(name = "length") // Dimensiones (opcional, para cálculos más precisos)
    private Double length;

    @Column(name = "width")
    private Double width;

    @Column(name = "height")
    private Double height;

    @Enumerated(EnumType.STRING) // Guarda el estatus como String en la DB
    @Column(nullable = false)
    private ProductStatus status = ProductStatus.ACTIVE; // Por defecto, activo

    @Column(name = "barcode", unique = true) // Código de barras, único
    private String barcode;

    @Column(name = "tax_rate") //Tasa de impuesto
    @PositiveOrZero
    private BigDecimal taxRate; // Porcentaje de impuesto (ej. 16.00 para 16%)

    // Podrías tener una relación con una entidad Tax si tienes varios tipos de impuestos
    //@ManyToOne
    //@JoinColumn(name = "tax_id")
    //private Tax tax;
}