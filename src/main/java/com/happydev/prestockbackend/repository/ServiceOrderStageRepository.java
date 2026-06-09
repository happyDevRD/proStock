package com.happydev.prestockbackend.repository;

import com.happydev.prestockbackend.entity.ServiceOrderStage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ServiceOrderStageRepository extends JpaRepository<ServiceOrderStage, Long> {

    List<ServiceOrderStage> findByServiceOrderIdOrderByEnteredAtAsc(Long serviceOrderId);

    void deleteByServiceOrderId(Long serviceOrderId);
}
