package com.happydev.prestockbackend.repository;


import com.happydev.prestockbackend.entity.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, Long> {
    // Métodos de consulta personalizados (opcional)
}
