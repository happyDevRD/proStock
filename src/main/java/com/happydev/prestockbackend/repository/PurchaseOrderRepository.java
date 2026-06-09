package com.happydev.prestockbackend.repository;

import com.happydev.prestockbackend.entity.PurchaseOrder;
import com.happydev.prestockbackend.entity.PurchaseOrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {
    List<PurchaseOrder> findByStatusIn(List<PurchaseOrderStatus> statuses);
}
