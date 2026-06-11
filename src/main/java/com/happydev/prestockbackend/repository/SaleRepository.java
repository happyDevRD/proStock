package com.happydev.prestockbackend.repository;

import com.happydev.prestockbackend.entity.Sale;
import com.happydev.prestockbackend.entity.SaleStatus;
import com.happydev.prestockbackend.entity.ServiceOrderType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SaleRepository extends JpaRepository<Sale, Long>, JpaSpecificationExecutor<Sale> {
    Optional<Sale> findByNcf(String ncf);

    Optional<Sale> findByIdempotencyKey(String idempotencyKey);

    @Query("""
            SELECT COUNT(s) FROM Sale s
            WHERE s.status = :status
              AND s.saleDate >= COALESCE(:start, s.saleDate)
              AND s.saleDate <= COALESCE(:end, s.saleDate)
            """)
    long countInRange(
            @Param("status") SaleStatus status,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("""
            SELECT COALESCE(SUM(s.montoTotal), 0) FROM Sale s
            WHERE s.status = com.happydev.prestockbackend.entity.SaleStatus.COMPLETED
              AND s.saleDate >= COALESCE(:start, s.saleDate)
              AND s.saleDate <= COALESCE(:end, s.saleDate)
            """)
    BigDecimal sumCompletedRevenue(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("""
            SELECT COALESCE(SUM(s.totalItbis), 0) FROM Sale s
            WHERE s.status = com.happydev.prestockbackend.entity.SaleStatus.COMPLETED
              AND s.saleDate >= COALESCE(:start, s.saleDate)
              AND s.saleDate <= COALESCE(:end, s.saleDate)
            """)
    BigDecimal sumCompletedTax(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("""
            SELECT COUNT(s) FROM Sale s
            WHERE s.status = com.happydev.prestockbackend.entity.SaleStatus.COMPLETED
              AND s.saleDate >= :start
              AND s.saleDate <= :end
            """)
    long countCompletedBetween(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("""
            SELECT COALESCE(SUM(s.montoTotal), 0) FROM Sale s
            WHERE s.status = com.happydev.prestockbackend.entity.SaleStatus.COMPLETED
              AND s.saleDate >= :start
              AND s.saleDate <= :end
            """)
    BigDecimal sumCompletedRevenueBetween(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query(value = """
            SELECT CAST(s.sale_date AS date) AS day,
                   COUNT(*) AS sale_count,
                   COALESCE(SUM(s.monto_total), 0) AS revenue,
                   COALESCE(SUM(s.total_itbis), 0) AS tax
            FROM sales s
            WHERE s.status = 'COMPLETED'
              AND s.sale_date >= COALESCE(:start, s.sale_date)
              AND s.sale_date <= COALESCE(:end, s.sale_date)
            GROUP BY CAST(s.sale_date AS date)
            ORDER BY revenue DESC
            LIMIT 7
            """, nativeQuery = true)
    List<Object[]> findTopCompletedDaysByRevenue(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query(value = """
            SELECT CAST(s.sale_date AS date) AS day,
                   COUNT(*) AS sale_count,
                   COALESCE(SUM(s.monto_total), 0) AS revenue,
                   COALESCE(SUM(s.total_itbis), 0) AS tax
            FROM sales s
            WHERE s.status = 'COMPLETED'
              AND s.sale_date >= :trendStart
            GROUP BY CAST(s.sale_date AS date)
            ORDER BY day ASC
            """, nativeQuery = true)
    List<Object[]> findCompletedDailyTrend(@Param("trendStart") LocalDateTime trendStart);

    @Query("SELECT COUNT(s) FROM Sale s WHERE s.status = com.happydev.prestockbackend.entity.SaleStatus.PARTIALLY_PAID")
    long countPartiallyPaid();

    @Query("SELECT COALESCE(SUM(s.montoTotal - s.paidAmount), 0) FROM Sale s WHERE s.status = com.happydev.prestockbackend.entity.SaleStatus.PARTIALLY_PAID")
    BigDecimal sumPendingBalance();

    List<Sale> findByServiceOrderIdOrderBySaleDateDesc(Long serviceOrderId);

    @Query("""
            SELECT COALESCE(SUM(s.montoTotal), 0) FROM Sale s
            WHERE s.serviceOrder IS NOT NULL
              AND s.serviceOrder.orderType = :orderType
              AND s.status IN (
                  com.happydev.prestockbackend.entity.SaleStatus.COMPLETED,
                  com.happydev.prestockbackend.entity.SaleStatus.PARTIALLY_PAID
              )
              AND s.saleDate >= :start AND s.saleDate < :end
            """)
    BigDecimal sumServiceOrderLinkedRevenueByOrderType(
            @Param("orderType") ServiceOrderType orderType,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("""
            SELECT COALESCE(SUM(s.montoTotal), 0) FROM Sale s
            WHERE s.serviceOrder IS NOT NULL
              AND s.status IN (
                  com.happydev.prestockbackend.entity.SaleStatus.COMPLETED,
                  com.happydev.prestockbackend.entity.SaleStatus.PARTIALLY_PAID
              )
              AND s.saleDate >= :start AND s.saleDate < :end
            """)
    BigDecimal sumServiceOrderLinkedRevenue(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );
}
