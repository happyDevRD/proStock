package com.happydev.prestockbackend.repository;

import com.happydev.prestockbackend.entity.CompanyConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CompanyConfigRepository extends JpaRepository<CompanyConfig, Long> {
    Optional<CompanyConfig> findFirstByOrderByIdAsc();
}
