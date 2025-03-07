package com.happydev.prestockbackend.repository;

import com.happydev.prestockbackend.entity.PurchaseOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PurchaseOrderItemRepository extends JpaRepository<PurchaseOrderItem, Long> {

}
