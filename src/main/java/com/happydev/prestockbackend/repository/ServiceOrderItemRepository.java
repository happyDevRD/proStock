package com.happydev.prestockbackend.repository;

import com.happydev.prestockbackend.entity.ServiceOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ServiceOrderItemRepository extends JpaRepository<ServiceOrderItem, Long> {
    List<ServiceOrderItem> findByServiceOrderIdOrderByPositionAscCreatedAtAsc(Long serviceOrderId);
    void deleteByServiceOrderId(Long serviceOrderId);
}
