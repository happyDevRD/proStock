package com.happydev.prestockbackend.repository;

import com.happydev.prestockbackend.entity.CreditNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CreditNoteRepository extends JpaRepository<CreditNote, Long> {
    List<CreditNote> findBySale_IdOrderByCreatedAtDesc(Long saleId);
}
