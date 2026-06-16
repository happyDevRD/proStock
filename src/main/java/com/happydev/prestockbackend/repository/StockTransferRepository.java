package com.happydev.prestockbackend.repository;

import com.happydev.prestockbackend.entity.StockTransfer;
import com.happydev.prestockbackend.entity.StockTransferStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockTransferRepository extends JpaRepository<StockTransfer, Long> {
    Page<StockTransfer> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<StockTransfer> findByStatusOrderByCreatedAtDesc(StockTransferStatus status, Pageable pageable);
}
