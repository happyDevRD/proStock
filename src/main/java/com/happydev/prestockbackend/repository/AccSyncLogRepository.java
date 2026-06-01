package com.happydev.prestockbackend.repository;

import com.happydev.prestockbackend.entity.AccSyncLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AccSyncLogRepository extends JpaRepository<AccSyncLog, Long> {

    boolean existsByEntityTypeAndEntityId(String entityType, Long entityId);
}
