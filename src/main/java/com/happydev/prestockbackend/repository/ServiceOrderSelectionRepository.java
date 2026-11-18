package com.happydev.prestockbackend.repository;

import com.happydev.prestockbackend.entity.ServiceOrderSelection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ServiceOrderSelectionRepository extends JpaRepository<ServiceOrderSelection, Long> {

    Optional<ServiceOrderSelection> findByToken(String token);

    Optional<ServiceOrderSelection> findByServiceOrderId(Long serviceOrderId);

    Optional<ServiceOrderSelection> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    long countByExtraStatus(String extraStatus);

    java.util.List<ServiceOrderSelection> findByExtraStatusOrderBySubmittedAtAsc(String extraStatus);
}
