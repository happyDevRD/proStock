package com.happydev.prestockbackend.repository;

import com.happydev.prestockbackend.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {
    // Método para buscar por email (útil para validaciones)
    Optional<Customer> findByEmail(String email); //Para validar si existe
    boolean existsByEmail(String email);
}
