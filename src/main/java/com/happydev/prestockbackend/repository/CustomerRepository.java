package com.happydev.prestockbackend.repository;

import com.happydev.prestockbackend.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {
    // Método para buscar por email (útil para validaciones)
    Optional<Customer> findByEmail(String email); //Para validar si existe
    boolean existsByEmail(String email);

    Optional<Customer> findFirstByEmailIgnoreCase(String email);

    /** Coincidencia por los últimos dígitos del teléfono, sin importar formato (809-555-1234, +1 809…). */
    @Query(value = """
            SELECT * FROM customers
            WHERE RIGHT(REGEXP_REPLACE(COALESCE(phone_number, ''), '[^0-9]', '', 'g'), 10) = :digits
            ORDER BY id LIMIT 1
            """, nativeQuery = true)
    Optional<Customer> findFirstByPhoneDigits(@Param("digits") String digits);

    Optional<Customer> findByIdempotencyKey(String idempotencyKey);

    Optional<Customer> findByEmailAndPortalEnabledTrue(String email);
}
