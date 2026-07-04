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
            SELECT s FROM Sale s
            LEFT JOIN FETCH s.customer
            WHERE s.status = :status
              AND s.ncf IS NOT NULL
              AND s.saleDate >= :start
              AND s.saleDate <= :end
            ORDER BY s.ncf
            """)
    List<Sale> findWithNcfByStatusInRange(
            @Param("status") SaleStatus status,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

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

    @Query(value = """
            SELECT s.customer_id,
                   CONCAT(c.first_name, ' ', c.last_name) AS customer_name,
                   COALESCE(SUM(s.monto_total), 0) AS revenue,
                   COUNT(s.id) AS sale_count
            FROM sales s
            JOIN customers c ON c.id = s.customer_id
            WHERE s.status = 'COMPLETED'
              AND s.sale_date >= COALESCE(:start, s.sale_date)
              AND s.sale_date <= COALESCE(:end, s.sale_date)
              AND s.customer_id IS NOT NULL
            GROUP BY s.customer_id, c.first_name, c.last_name
            ORDER BY revenue DESC
            LIMIT 5
            """, nativeQuery = true)
    java.util.List<Object[]> findTopCustomersByRevenue(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

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

    /** Ventas completadas con descuento superior a un umbral (para detección de anomalías). */
    @Query("""
            SELECT s FROM Sale s
            WHERE s.status = com.happydev.prestockbackend.entity.SaleStatus.COMPLETED
              AND s.discountAmount > :minDiscount
              AND s.saleDate >= :since
            ORDER BY s.discountAmount DESC
            """)
    List<Sale> findCompletedWithHighDiscount(
            @Param("minDiscount") java.math.BigDecimal minDiscount,
            @Param("since") LocalDateTime since
    );

    /** Ventas asignadas a un empleado en un período (para cálculo de comisiones). */
    @Query("""
            SELECT s FROM Sale s
            WHERE s.employee.id = :employeeId
              AND s.saleDate BETWEEN :from AND :to
              AND s.status IN (
                com.happydev.prestockbackend.entity.SaleStatus.COMPLETED,
                com.happydev.prestockbackend.entity.SaleStatus.PARTIALLY_PAID
              )
            ORDER BY s.saleDate DESC
            """)
    List<Sale> findByEmployeeIdAndDateRange(
            @Param("employeeId") Long employeeId,
            @Param("from") java.time.LocalDateTime from,
            @Param("to") java.time.LocalDateTime to
    );
}
