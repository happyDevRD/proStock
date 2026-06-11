package com.happydev.prestockbackend.entity;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;

@Entity
@Table(name = "purchase_orders")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class PurchaseOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) // O EAGER, según necesites
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Column(name = "order_date", nullable = false)
    private LocalDate orderDate; // Fecha de la orden

    @Column(name = "reception_date")
    private LocalDateTime receptionDate; //Considerar usar LocalDateTime para registrar la hora


    @Enumerated(EnumType.STRING) // Guarda el estado como String
    @Column(nullable = false)
    private PurchaseOrderStatus status; // Nuevo campo para el estado

    @Column(name = "paid_amount", nullable = false)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    /** NCF de la factura del proveedor — necesario para el reporte DGII 606. */
    @Column(name = "ncf_proveedor", length = 19)
    private String ncfProveedor;

    /** Código DGII 606 "tipo de bienes y servicios comprados" (01-11). */
    @Column(name = "tipo_bienes_servicios", nullable = false, length = 2)
    private String tipoBienesServicios = "09";

    @Column(name = "total_itbis", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalItbis = BigDecimal.ZERO;

    @Column(name = "fecha_pago")
    private LocalDate fechaPago;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 20)
    private PaymentMethod paymentMethod;

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PurchaseOrderItem> items = new ArrayList<>(); // Lista de items

    // Método para calcular el total (opcional, pero útil)
    public double getTotal() {
        return items.stream().mapToDouble(item -> item.getUnitPrice() * item.getQuantity()).sum();
    }

    // ... (otros métodos, si son necesarios) ...
}
