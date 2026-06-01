package com.happydev.prestockbackend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "acc_journal_entry_lines")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AccJournalEntryLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_entry_id", nullable = false)
    private AccJournalEntry journalEntry;

    @Column(name = "account_id")
    private Long accountId;

    @Column(precision = 18, scale = 2)
    private BigDecimal debit = BigDecimal.ZERO;

    @Column(precision = 18, scale = 2)
    private BigDecimal credit = BigDecimal.ZERO;

    @Column(length = 255)
    private String description;
}
