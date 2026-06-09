package com.happydev.prestockbackend.repository;

import com.happydev.prestockbackend.entity.SupplierPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface SupplierPaymentRepository extends JpaRepository<SupplierPayment, Long> {
    List<SupplierPayment> findByPurchaseOrderIdOrderByPaymentDateAsc(Long purchaseOrderId);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM SupplierPayment p WHERE p.purchaseOrder.id = :poId")
    BigDecimal sumAmountByPurchaseOrderId(@Param("poId") Long poId);
}
