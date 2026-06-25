package com.happydev.prestockbackend.service;

import java.time.LocalDate;

public interface AccountingExportService {
    byte[] exportTrialBalance(LocalDate from, LocalDate to);
    byte[] exportIncomeStatement(LocalDate from, LocalDate to);
    byte[] exportBalanceSheet(LocalDate asOfDate);
    byte[] exportLedger(Long accountId, LocalDate from, LocalDate to);
}
