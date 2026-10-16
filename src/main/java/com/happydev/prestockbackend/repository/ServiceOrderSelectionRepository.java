package com.happydev.prestockbackend.repository;

import com.happydev.prestockbackend.entity.ServiceOrderSelection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ServiceOrderSelectionRepository extends JpaRepository<ServiceOrderSelection, Long> {

    Optional<ServiceOrderSelection> findByToken(String token);

    Optional<ServiceOrderSelection> findByServiceOrderId(Long serviceOrderId);
}
