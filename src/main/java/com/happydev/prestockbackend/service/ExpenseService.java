package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.dto.ExpenseDto;
import com.happydev.prestockbackend.entity.Expense;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ExpenseService {
    Page<ExpenseDto> findAll(@NonNull String description, String category, @NonNull Pageable pageable);
    Optional<ExpenseDto> findById(@NonNull Long id);
    ExpenseDto create(@NonNull ExpenseDto dto);
    ExpenseDto update(@NonNull Long id, @NonNull ExpenseDto dto);
    void delete(@NonNull Long id);
    List<Expense> findWithNcfInRange(@NonNull LocalDate from, @NonNull LocalDate to);
}
