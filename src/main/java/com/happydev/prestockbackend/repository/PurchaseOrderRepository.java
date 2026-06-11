package com.happydev.prestockbackend.repository;

import com.happydev.prestockbackend.entity.PurchaseOrder;
import com.happydev.prestockbackend.entity.PurchaseOrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {
    List<PurchaseOrder> findByStatusIn(List<PurchaseOrderStatus> statuses);

    @Query("""
            SELECT DISTINCT po FROM PurchaseOrder po
            LEFT JOIN FETCH po.supplier
            LEFT JOIN FETCH po.items i
            LEFT JOIN FETCH i.product
            WHERE po.ncfProveedor IS NOT NULL
              AND po.orderDate >= :start
              AND po.orderDate <= :end
            ORDER BY po.ncfProveedor
            """)
    List<PurchaseOrder> findWithNcfInRange(
            @Param("start") LocalDate start,
            @Param("end") LocalDate end
    );
}
