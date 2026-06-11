package com.happydev.prestockbackend.repository;

import com.happydev.prestockbackend.entity.ServiceOrder;
import com.happydev.prestockbackend.entity.ServiceOrderStatus;
import com.happydev.prestockbackend.entity.ServiceOrderType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ServiceOrderRepository extends JpaRepository<ServiceOrder, Long>, JpaSpecificationExecutor<ServiceOrder> {

    List<ServiceOrder> findAllByOrderByCreatedAtDesc();

    List<ServiceOrder> findByStatusInOrderByCreatedAtDesc(List<ServiceOrderStatus> statuses);

    List<ServiceOrder> findByOrderTypeOrderByCreatedAtDesc(ServiceOrderType orderType);

    List<ServiceOrder> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    List<ServiceOrder> findByCustomerIdAndOrderTypeOrderByCreatedAtDesc(
            Long customerId, ServiceOrderType orderType);

    long countByStatus(ServiceOrderStatus status);

    long countByOrderTypeAndStatus(ServiceOrderType orderType, ServiceOrderStatus status);

    @Query("""
            SELECT COUNT(o) FROM ServiceOrder o
            WHERE o.orderType = :orderType
              AND o.status = com.happydev.prestockbackend.entity.ServiceOrderStatus.COMPLETED
              AND o.updatedAt >= :since
            """)
    long countCompletedSinceByOrderType(
            @Param("orderType") ServiceOrderType orderType,
            @Param("since") LocalDateTime since
    );

    @Query("""
            SELECT COUNT(o) FROM ServiceOrder o
            WHERE o.status = com.happydev.prestockbackend.entity.ServiceOrderStatus.COMPLETED
              AND o.updatedAt >= :since
            """)
    long countCompletedSince(@Param("since") LocalDateTime since);

    @Query("""
            SELECT COUNT(o) FROM ServiceOrder o
            WHERE o.orderType = :orderType
              AND o.createdAt >= :start AND o.createdAt < :end
            """)
    long countCreatedInPeriodByOrderType(
            @Param("orderType") ServiceOrderType orderType,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("""
            SELECT COUNT(o) FROM ServiceOrder o
            WHERE o.createdAt >= :start AND o.createdAt < :end
            """)
    long countCreatedInPeriod(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("""
            SELECT COUNT(o) FROM ServiceOrder o
            WHERE o.orderType = :orderType
              AND o.status = :status AND o.updatedAt >= :start AND o.updatedAt < :end
            """)
    long countByOrderTypeAndStatusUpdatedInPeriod(
            @Param("orderType") ServiceOrderType orderType,
            @Param("status") ServiceOrderStatus status,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("""
            SELECT COUNT(o) FROM ServiceOrder o
            WHERE o.status = :status AND o.updatedAt >= :start AND o.updatedAt < :end
            """)
    long countByStatusUpdatedInPeriod(
            @Param("status") ServiceOrderStatus status,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("""
            SELECT o FROM ServiceOrder o
            WHERE o.orderType = :orderType
              AND o.status = com.happydev.prestockbackend.entity.ServiceOrderStatus.COMPLETED
              AND o.updatedAt >= :start AND o.updatedAt < :end
            """)
    List<ServiceOrder> findCompletedInPeriodByOrderType(
            @Param("orderType") ServiceOrderType orderType,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("""
            SELECT o FROM ServiceOrder o
            WHERE o.status = com.happydev.prestockbackend.entity.ServiceOrderStatus.COMPLETED
              AND o.updatedAt >= :start AND o.updatedAt < :end
            """)
    List<ServiceOrder> findCompletedInPeriod(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("""
            SELECT o.orderType, COUNT(o) FROM ServiceOrder o
            WHERE o.createdAt >= :start AND o.createdAt < :end
            GROUP BY o.orderType
            """)
    List<Object[]> countCreatedGroupByType(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("""
            SELECT o.orderType, COUNT(o) FROM ServiceOrder o
            WHERE o.status = com.happydev.prestockbackend.entity.ServiceOrderStatus.COMPLETED
              AND o.updatedAt >= :start AND o.updatedAt < :end
            GROUP BY o.orderType
            """)
    List<Object[]> countCompletedGroupByType(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
