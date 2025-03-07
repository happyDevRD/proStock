package com.happydev.prestockbackend.repository;

import com.happydev.prestockbackend.entity.Category;
import com.happydev.prestockbackend.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    // Puedes agregar métodos de consulta personalizados aquí si los necesitas.
    // Por ejemplo:
    // Optional<Category> findByName(String name);
}