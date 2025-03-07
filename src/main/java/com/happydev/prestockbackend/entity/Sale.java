package com.happydev.prestockbackend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "sales")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Sale {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sale_date", nullable = false)
    private LocalDateTime saleDate; // Fecha y hora de la venta

    @ManyToOne(fetch = FetchType.LAZY) // Relación con Customer
    @JoinColumn(name = "customer_id") //  Puede ser nullable si permites ventas sin cliente
    private Customer customer;


    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SaleItem> items = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false) //Por defecto que sea PENDING
    private SaleStatus status = SaleStatus.PENDING;

    // Método para calcular el total (opcional, pero útil)
    public double getTotal() {
        return items.stream().mapToDouble(item -> item.getUnitPrice() * item.getQuantity()).sum();
    }

    // ... (otros métodos, si son necesarios) ...
}
