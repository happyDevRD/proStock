package com.happydev.prestockbackend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "service_orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ServiceOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_number", nullable = false, unique = true, length = 20)
    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false, length = 20)
    private ServiceOrderType orderType;

    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ServiceOrderStatus status = ServiceOrderStatus.OPEN;

    @Column(name = "current_stage", nullable = false, length = 50)
    private String currentStage;

    @Column(name = "appointment_date")
    private LocalDateTime appointmentDate;

    @Column(name = "estimated_delivery")
    private LocalDate estimatedDelivery;

    @Column(name = "estimated_amount", precision = 18, scale = 2)
    private BigDecimal estimatedAmount;

    @Column(name = "budget_approved", nullable = false)
    private boolean budgetApproved = false;

    @Column(name = "budget_approved_at")
    private LocalDateTime budgetApprovedAt;

    @Column(name = "deposit_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal depositAmount = BigDecimal.ZERO;

    // Repair device info
    @Column(name = "device_brand", length = 100)
    private String deviceBrand;

    @Column(name = "device_model", length = 100)
    private String deviceModel;

    @Column(name = "device_serial", length = 100)
    private String deviceSerial;

    @Column(name = "device_condition", length = 500)
    private String deviceCondition;

    @Column(name = "problem_description", columnDefinition = "TEXT")
    private String problemDescription;

    @Column(name = "internal_notes", columnDefinition = "TEXT")
    private String internalNotes;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
