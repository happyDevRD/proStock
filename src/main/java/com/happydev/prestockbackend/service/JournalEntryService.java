package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.dto.CreateJournalEntryRequest;
import com.happydev.prestockbackend.dto.JournalEntryDto;

import java.time.LocalDate;
import java.util.List;

public interface JournalEntryService {

    List<JournalEntryDto> findAll(String status, LocalDate from, LocalDate to);

    JournalEntryDto findById(Long id);

    JournalEntryDto create(CreateJournalEntryRequest req, String username);

    JournalEntryDto update(Long id, CreateJournalEntryRequest req);

    JournalEntryDto post(Long id, String username);

    JournalEntryDto reverse(Long id, String username);

    void delete(Long id);
}
