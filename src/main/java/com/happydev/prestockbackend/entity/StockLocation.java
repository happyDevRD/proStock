package com.happydev.prestockbackend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "stock_locations", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"product_id", "location_id"})
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class StockLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @Column(nullable = false)
    private int quantity = 0;
}
