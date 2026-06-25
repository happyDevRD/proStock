package com.happydev.prestockbackend.repository;

import com.happydev.prestockbackend.entity.Expense;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    @Query("""
            SELECT e FROM Expense e
            LEFT JOIN FETCH e.supplier
            WHERE e.expenseDate BETWEEN :from AND :to
            ORDER BY e.expenseDate DESC
            """)
    List<Expense> findInRange(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Para el reporte DGII 606: solo gastos con NCF de proveedor en el período. */
    @Query("""
            SELECT e FROM Expense e
            LEFT JOIN FETCH e.supplier
            WHERE e.ncfProveedor IS NOT NULL
              AND e.expenseDate BETWEEN :from AND :to
            ORDER BY e.expenseDate ASC
            """)
    List<Expense> findWithNcfInRange(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query(value = """
            SELECT e FROM Expense e
            LEFT JOIN FETCH e.supplier
            WHERE (:description IS NULL OR LOWER(e.description) LIKE LOWER(CONCAT('%', :description, '%')))
              AND (:category IS NULL OR e.category = :category)
            """,
            countQuery = "SELECT COUNT(e) FROM Expense e WHERE (:description IS NULL OR LOWER(e.description) LIKE LOWER(CONCAT('%', :description, '%'))) AND (:category IS NULL OR e.category = :category)")
    Page<Expense> findFiltered(
            @Param("description") String description,
            @Param("category") String category,
            Pageable pageable);
}
