package com.happydev.prestockbackend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "stock_movements")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class StockMovement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime movementDate;

    @NotNull
    @Column(nullable = false)
    private int quantityChange; // Positivo para entradas, negativo para salidas

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StockMovementType type; // Enum: IN, OUT, ADJUSTMENT, TRANSFER

    @Column
    private String reason; // Descripción del movimiento (opcional, pero MUY útil)

    //Relaciones opcionales:
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_id") //Si es por compra
    private PurchaseOrder purchaseOrder;  // Necesitarás la entidad PurchaseOrder

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id") //Si es por venta.
    private Sale sale;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id") // Quién realizó el movimiento (si tienes usuarios)
    private User user; // Necesitarás la entidad User, y probablemente Spring Security

    //Si tienes ubicaciones, para transferencias.
    @Column(name="source_location_id")
    private Long sourceLocationId; //Ubicacion origen ID

    @Column(name="destination_location_id")
    private Long destinationLocationId;//Ubicacion destino ID

    // Nuevos campos:
    @Column(name = "batch_number") // Número de lote
    private String batchNumber;

    @Column(name = "expiration_date") // Fecha de caducidad (para el lote)
    private LocalDateTime expirationDate;

    @Column(name = "unit_cost") // Costo unitario *en el momento del movimiento*
    private BigDecimal unitCost;
    // Campo para el stock antes del movimiento.
    @Column(name = "stock_before")
    private Integer stockBefore;

    // Campo para el stock después del movimiento
    @Column(name = "stock_after")
    private Integer stockAfter;
}