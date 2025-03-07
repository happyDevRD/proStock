package com.happydev.prestockbackend.dto;
import com.happydev.prestockbackend.entity.ProductStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;


import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
public class ProductDto {
    private Long id;

    @NotBlank(message = "El SKU no puede estar vacío")
    @Size(min = 3, max = 50, message = "El SKU debe tener entre 3 y 50 caracteres")
    private String sku;

    @NotBlank(message = "El nombre del producto no puede estar vacío")
    @Size(min = 2, max = 255, message = "El nombre debe tener entre 2 y 255 caracteres")
    private String name;

    @Size(max = 1000, message = "La descripción no puede exceder los 1000 caracteres")
    private String description;

    @NotNull(message = "El ID de la categoría no puede ser nulo")
    private Long categoryId;

    @NotNull(message = "El ID del proveedor no puede ser nulo")
    private Long supplierId;

    @NotNull(message = "El precio de costo no puede ser nulo")
    @Positive(message = "El precio de costo debe ser mayor que cero")
    private BigDecimal costPrice;

    @NotNull(message = "El precio de venta no puede ser nulo")
    @Positive(message = "El precio de venta debe ser mayor que cero")
    private BigDecimal sellingPrice;

    @NotNull(message = "El stock no puede ser nulo")
    @PositiveOrZero(message = "El stock no puede ser negativo")
    private Integer stock;

    @NotNull(message = "El stock mínimo no puede ser nulo")
    @PositiveOrZero(message = "El stock mínimo no puede ser negativo")
    private Integer minStock;

    @Future(message = "La fecha de vencimiento debe ser una fecha futura")
    private LocalDate expirationDate;

    private Boolean forSale;

    @Valid
    @NotEmpty(message = "Debe haber al menos una imagen del producto")
    private List<ProductImageDto> images;


    // Nuevos Atributos
    @NotBlank(message = "La unidad de medida no puede estar vacía")
    private String unitOfMeasure;

    private String location;

    @Positive(message = "El peso debe ser mayor que cero")
    private Double weight;

    private Double length;
    private Double width;
    private Double height;

    @NotNull(message = "El estatus no puede ser nulo") //Aunque tenga un valor por defecto
    private ProductStatus status;

    private String barcode; // No se valida como unico aqui

    @PositiveOrZero(message= "La tasa de impuesto no puede ser negativa")
    private BigDecimal taxRate;
}