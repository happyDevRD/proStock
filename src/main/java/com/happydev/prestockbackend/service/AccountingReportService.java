package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.dto.FinancialStatementDto;
import com.happydev.prestockbackend.dto.LedgerEntryDto;
import com.happydev.prestockbackend.dto.TrialBalanceRowDto;

import java.time.LocalDate;
import java.util.List;

public interface AccountingReportService {

    List<TrialBalanceRowDto> getTrialBalance(LocalDate startDate, LocalDate endDate);

    FinancialStatementDto getIncomeStatement(LocalDate startDate, LocalDate endDate);

    FinancialStatementDto getBalanceSheet(LocalDate asOfDate);

    List<LedgerEntryDto> getLedger(Long accountId, LocalDate startDate, LocalDate endDate);
}
