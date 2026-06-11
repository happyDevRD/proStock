package com.happydev.prestockbackend.repository;

import com.happydev.prestockbackend.entity.CreditNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface CreditNoteRepository extends JpaRepository<CreditNote, Long> {
    List<CreditNote> findBySale_IdOrderByCreatedAtDesc(Long saleId);

    @Query("""
            SELECT cn FROM CreditNote cn
            LEFT JOIN FETCH cn.sale s
            LEFT JOIN FETCH s.customer
            WHERE cn.createdAt >= :start
              AND cn.createdAt <= :end
            ORDER BY cn.ncf
            """)
    List<CreditNote> findInRangeWithSale(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );
}
