package com.happydev.prestockbackend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "acc_sync_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AccSyncLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_type", nullable = false, length = 30)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(name = "journal_entry_id")
    private Long journalEntryId;

    @Column(name = "synced_at")
    private LocalDateTime syncedAt;
}
