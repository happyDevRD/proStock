package com.happydev.prestockbackend.repository;

import com.happydev.prestockbackend.entity.AccJournalEntryLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AccJournalEntryLineRepository extends JpaRepository<AccJournalEntryLine, Long> {

    List<AccJournalEntryLine> findByJournalEntry_Id(Long journalEntryId);
}
