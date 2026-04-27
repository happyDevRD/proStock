package com.happydev.prestockbackend.entity;

import com.happydev.prestockbackend.entity.converter.IndicadorFacturacionConverter;
import com.happydev.prestockbackend.entity.converter.TipoBienServicioConverter;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
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
    private LocalDate expirationDate;

    @NotNull
    private Boolean forSale = false;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductImage> images;

    @Convert(converter = IndicadorFacturacionConverter.class)
    @Column(name = "indicador_facturacion", nullable = false)
    @NotNull
    private IndicadorFacturacion indicadorFacturacion = IndicadorFacturacion.EXENTO;

    @Convert(converter = TipoBienServicioConverter.class)
    @Column(name = "tipo_bien_servicio", nullable = false)
    @NotNull
    private TipoBienServicio tipoBienServicio = TipoBienServicio.BIEN;

    @Column(name = "unit_of_measure", nullable = false)
    @NotNull
    @Positive
    private Integer unidadMedida;

    @Column(name = "location")
    private String location;

    @Column(name = "weight")
    @Positive
    private Double weight;

    @Column(name = "length")
    private Double length;

    @Column(name = "width")
    private Double width;

    @Column(name = "height")
    private Double height;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductStatus status = ProductStatus.ACTIVE;

    @Column(name = "barcode", unique = true)
    private String barcode;

    @Column(name = "tax_rate")
    @PositiveOrZero
    private BigDecimal taxRate;
}