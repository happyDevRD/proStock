package com.happydev.prestockbackend.entity;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

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

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PurchaseOrderItem> items = new ArrayList<>(); // Lista de items

    // Método para calcular el total (opcional, pero útil)
    public double getTotal() {
        return items.stream().mapToDouble(item -> item.getUnitPrice() * item.getQuantity()).sum();
    }

    // ... (otros métodos, si son necesarios) ...
}
