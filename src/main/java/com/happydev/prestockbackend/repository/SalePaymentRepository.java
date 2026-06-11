package com.happydev.prestockbackend.repository;

import com.happydev.prestockbackend.entity.SalePayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface SalePaymentRepository extends JpaRepository<SalePayment, Long> {
    List<SalePayment> findBySaleIdOrderByPaymentDateAsc(Long saleId);

    List<SalePayment> findBySaleIdIn(List<Long> saleIds);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM SalePayment p WHERE p.sale.id = :saleId")
    BigDecimal sumAmountBySaleId(@Param("saleId") Long saleId);
}
