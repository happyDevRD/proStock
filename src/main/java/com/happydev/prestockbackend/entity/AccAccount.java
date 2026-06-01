package com.happydev.prestockbackend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "acc_accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AccAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccAccountType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AccAccountNature nature;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "allows_transactions")
    private boolean allowsTransactions = true;

    @Column(name = "is_system")
    private boolean isSystem = false;

    @Column(name = "active")
    private boolean active = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
