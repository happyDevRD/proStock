package com.happydev.prestockbackend.repository;

import com.happydev.prestockbackend.entity.Location;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LocationRepository extends JpaRepository<Location, Long> {
    List<Location> findAllByOrderByNameAsc();
    List<Location> findByActiveTrue();
    Optional<Location> findByIsDefaultTrue();
    boolean existsByCode(String code);
}
