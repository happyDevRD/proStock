package com.happydev.prestockbackend.repository;

import com.happydev.prestockbackend.entity.AccEntryStatus;
import com.happydev.prestockbackend.entity.AccJournalEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface AccJournalEntryRepository extends JpaRepository<AccJournalEntry, Long> {

    List<AccJournalEntry> findByStatusOrderByEntryDateDesc(AccEntryStatus status);

    List<AccJournalEntry> findByStatusAndEntryDateBetweenOrderByEntryDateDesc(
            AccEntryStatus status,
            LocalDate from,
            LocalDate to
    );
}
