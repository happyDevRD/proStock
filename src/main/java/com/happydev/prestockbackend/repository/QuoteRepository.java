package com.happydev.prestockbackend.repository;

import com.happydev.prestockbackend.entity.Quote;
import com.happydev.prestockbackend.entity.QuoteStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public interface QuoteRepository extends JpaRepository<Quote, Long> {

    Page<Quote> findByStatusOrderByQuoteDateDesc(QuoteStatus status, Pageable pageable);

    Page<Quote> findAllByOrderByQuoteDateDesc(Pageable pageable);

    Page<Quote> findByCustomerIdOrderByQuoteDateDesc(Long customerId, Pageable pageable);

    @Query("SELECT COUNT(q) FROM Quote q WHERE q.status = :status")
    long countByStatus(@Param("status") QuoteStatus status);

    @Modifying
    @Query("UPDATE Quote q SET q.status = com.happydev.prestockbackend.entity.QuoteStatus.EXPIRED WHERE q.expiryDate < :today AND q.status IN (com.happydev.prestockbackend.entity.QuoteStatus.DRAFT, com.happydev.prestockbackend.entity.QuoteStatus.SENT)")
    int expireOverdue(@Param("today") LocalDate today);
}
