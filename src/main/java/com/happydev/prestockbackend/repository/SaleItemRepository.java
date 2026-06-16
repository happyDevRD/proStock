package com.happydev.prestockbackend.repository;

import com.happydev.prestockbackend.entity.SaleItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SaleItemRepository extends JpaRepository<SaleItem, Long> {

    @Query(value = """
            SELECT si.product_id,
                   COALESCE(MAX(si.product_name), MAX(p.name)) AS product_name,
                   COALESCE(SUM(si.unit_price * si.quantity), 0) AS revenue,
                   COALESCE(SUM(si.quantity), 0) AS total_qty
            FROM sale_items si
            JOIN sales s ON s.id = si.sale_id
            LEFT JOIN products p ON p.id = si.product_id
            WHERE s.status = 'COMPLETED'
              AND s.sale_date >= COALESCE(:start, s.sale_date)
              AND s.sale_date <= COALESCE(:end, s.sale_date)
            GROUP BY si.product_id
            ORDER BY revenue DESC
            LIMIT 5
            """, nativeQuery = true)
    List<Object[]> findTopProductsByRevenue(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );
}

