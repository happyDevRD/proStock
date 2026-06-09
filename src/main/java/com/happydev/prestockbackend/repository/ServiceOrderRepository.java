package com.happydev.prestockbackend.repository;

import com.happydev.prestockbackend.entity.ServiceOrder;
import com.happydev.prestockbackend.entity.ServiceOrderStatus;
import com.happydev.prestockbackend.entity.ServiceOrderType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ServiceOrderRepository extends JpaRepository<ServiceOrder, Long> {

    List<ServiceOrder> findAllByOrderByCreatedAtDesc();

    List<ServiceOrder> findByStatusInOrderByCreatedAtDesc(List<ServiceOrderStatus> statuses);

    List<ServiceOrder> findByOrderTypeOrderByCreatedAtDesc(ServiceOrderType orderType);

    List<ServiceOrder> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    long countByStatus(ServiceOrderStatus status);
}
