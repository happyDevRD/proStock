package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.dto.FinancialStatementDto;
import com.happydev.prestockbackend.dto.LedgerEntryDto;
import com.happydev.prestockbackend.dto.TrialBalanceRowDto;
import com.happydev.prestockbackend.entity.*;
import com.happydev.prestockbackend.repository.AccAccountRepository;
import com.happydev.prestockbackend.repository.AccJournalEntryLineRepository;
import com.happydev.prestockbackend.repository.AccJournalEntryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class AccountingReportServiceImpl implements AccountingReportService {

    private final AccAccountRepository accountRepository;
    private final AccJournalEntryRepository entryRepository;
    private final AccJournalEntryLineRepository lineRepository;

    public AccountingReportServiceImpl(AccAccountRepository accountRepository,
                                       AccJournalEntryRepository entryRepository,
                                       AccJournalEntryLineRepository lineRepository) {
        this.accountRepository = accountRepository;
        this.entryRepository = entryRepository;
        this.lineRepository = lineRepository;
    }

    // -------------------------------------------------------
    // Trial Balance
    // -------------------------------------------------------

    @Override
    public List<TrialBalanceRowDto> getTrialBalance(LocalDate startDate, LocalDate endDate) {
        List<AccJournalEntry> postedEntries = getPostedEntriesInRange(startDate, endDate);

        Map<Long, BigDecimal[]> totals = new HashMap<>(); // [0]=debit, [1]=credit
        for (AccJournalEntry entry : postedEntries) {
            for (AccJournalEntryLine line : entry.getLines()) {
                if (line.getAccountId() == null) continue;
                totals.computeIfAbsent(line.getAccountId(), k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                BigDecimal[] acc = totals.get(line.getAccountId());
                acc[0] = acc[0].add(safeVal(line.getDebit()));
                acc[1] = acc[1].add(safeVal(line.getCredit()));
            }
        }

        Map<Long, AccAccount> accountMap = accountRepository.findAll().stream()
                .collect(Collectors.toMap(AccAccount::getId, a -> a));

        return totals.entrySet().stream()
                .map(e -> {
                    AccAccount account = accountMap.get(e.getKey());
                    String code = account != null ? account.getCode() : e.getKey().toString();
                    String name = account != null ? account.getName() : "Unknown";
                    String type = account != null ? account.getType().name() : "";
                    BigDecimal dr = e.getValue()[0];
                    BigDecimal cr = e.getValue()[1];
                    BigDecimal balance = dr.subtract(cr);
                    return new TrialBalanceRowDto(code, name, type, dr, cr, balance);
                })
                .sorted(Comparator.comparing(TrialBalanceRowDto::code))
                .toList();
    }

    // -------------------------------------------------------
    // Income Statement
    // -------------------------------------------------------

    @Override
    public FinancialStatementDto getIncomeStatement(LocalDate startDate, LocalDate endDate) {
        List<AccJournalEntry> postedEntries = getPostedEntriesInRange(startDate, endDate);

        Map<Long, BigDecimal[]> totals = aggregateLines(postedEntries);
        Map<Long, AccAccount> accountMap = accountRepository.findAll().stream()
                .collect(Collectors.toMap(AccAccount::getId, a -> a));

        // Revenue section
        FinancialStatementDto.Section revenueSection = new FinancialStatementDto.Section("Ingresos", "REVENUE");
        BigDecimal totalRevenue = BigDecimal.ZERO;

        // Expense section
        FinancialStatementDto.Section expenseSection = new FinancialStatementDto.Section("Gastos", "EXPENSE");
        BigDecimal totalExpenses = BigDecimal.ZERO;

        for (Map.Entry<Long, BigDecimal[]> e : totals.entrySet()) {
            AccAccount account = accountMap.get(e.getKey());
            if (account == null) continue;
            BigDecimal dr = e.getValue()[0];
            BigDecimal cr = e.getValue()[1];

            if (account.getType() == AccAccountType.REVENUE) {
                // Revenue = credits - debits
                BigDecimal amount = cr.subtract(dr);
                revenueSection.getItems().add(
                        new FinancialStatementDto.LineItem(account.getCode(), account.getName(), amount));
                totalRevenue = totalRevenue.add(amount);
            } else if (account.getType() == AccAccountType.EXPENSE) {
                // Expense = debits - credits
                BigDecimal amount = dr.subtract(cr);
                expenseSection.getItems().add(
                        new FinancialStatementDto.LineItem(account.getCode(), account.getName(), amount));
                totalExpenses = totalExpenses.add(amount);
            }
        }

        revenueSection.getItems().sort(Comparator.comparing(FinancialStatementDto.LineItem::getCode));
        revenueSection.setSubtotal(totalRevenue);

        expenseSection.getItems().sort(Comparator.comparing(FinancialStatementDto.LineItem::getCode));
        expenseSection.setSubtotal(totalExpenses);

        FinancialStatementDto dto = new FinancialStatementDto();
        dto.getSections().add(revenueSection);
        dto.getSections().add(expenseSection);
        dto.setTotalRevenue(totalRevenue);
        dto.setTotalExpenses(totalExpenses);
        dto.setNetIncome(totalRevenue.subtract(totalExpenses));
        dto.setPeriodLabel(formatPeriod(startDate, endDate));

        return dto;
    }

    // -------------------------------------------------------
    // Balance Sheet
    // -------------------------------------------------------

    @Override
    public FinancialStatementDto getBalanceSheet(LocalDate asOfDate) {
        LocalDate cutoff = asOfDate != null ? asOfDate : LocalDate.now();

        // Cumulative: all posted entries up to asOfDate
        List<AccJournalEntry> postedEntries = entryRepository
                .findByStatusAndEntryDateBetweenOrderByEntryDateDesc(
                        AccEntryStatus.POSTED, LocalDate.of(1900, 1, 1), cutoff);

        Map<Long, BigDecimal[]> totals = aggregateLines(postedEntries);
        Map<Long, AccAccount> accountMap = accountRepository.findAll().stream()
                .collect(Collectors.toMap(AccAccount::getId, a -> a));

        FinancialStatementDto.Section assetSection = new FinancialStatementDto.Section("Activos", "ASSET");
        FinancialStatementDto.Section liabilitySection = new FinancialStatementDto.Section("Pasivos", "LIABILITY");
        FinancialStatementDto.Section equitySection = new FinancialStatementDto.Section("Patrimonio", "EQUITY");

        BigDecimal totalAssets = BigDecimal.ZERO;
        BigDecimal totalLiabilities = BigDecimal.ZERO;
        BigDecimal totalEquity = BigDecimal.ZERO;

        for (Map.Entry<Long, BigDecimal[]> e : totals.entrySet()) {
            AccAccount account = accountMap.get(e.getKey());
            if (account == null) continue;
            BigDecimal dr = e.getValue()[0];
            BigDecimal cr = e.getValue()[1];

            switch (account.getType()) {
                case ASSET -> {
                    // Normal debit balance
                    BigDecimal amount = dr.subtract(cr);
                    assetSection.getItems().add(
                            new FinancialStatementDto.LineItem(account.getCode(), account.getName(), amount));
                    totalAssets = totalAssets.add(amount);
                }
                case LIABILITY -> {
                    // Normal credit balance
                    BigDecimal amount = cr.subtract(dr);
                    liabilitySection.getItems().add(
                            new FinancialStatementDto.LineItem(account.getCode(), account.getName(), amount));
                    totalLiabilities = totalLiabilities.add(amount);
                }
                case EQUITY -> {
                    BigDecimal amount = cr.subtract(dr);
                    equitySection.getItems().add(
                            new FinancialStatementDto.LineItem(account.getCode(), account.getName(), amount));
                    totalEquity = totalEquity.add(amount);
                }
                default -> { /* REVENUE/EXPENSE not shown in BS */ }
            }
        }

        assetSection.getItems().sort(Comparator.comparing(FinancialStatementDto.LineItem::getCode));
        assetSection.setSubtotal(totalAssets);

        liabilitySection.getItems().sort(Comparator.comparing(FinancialStatementDto.LineItem::getCode));
        liabilitySection.setSubtotal(totalLiabilities);

        equitySection.getItems().sort(Comparator.comparing(FinancialStatementDto.LineItem::getCode));
        equitySection.setSubtotal(totalEquity);

        FinancialStatementDto dto = new FinancialStatementDto();
        dto.getSections().add(assetSection);
        dto.getSections().add(liabilitySection);
        dto.getSections().add(equitySection);
        dto.setTotalAssets(totalAssets);
        dto.setTotalLiabilities(totalLiabilities);
        dto.setTotalEquity(totalEquity);
        dto.setPeriodLabel("Al " + cutoff);

        return dto;
    }

    // -------------------------------------------------------
    // Ledger
    // -------------------------------------------------------

    @Override
    public List<LedgerEntryDto> getLedger(Long accountId, LocalDate startDate, LocalDate endDate) {
        AccAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Account not found: " + accountId));

        List<AccJournalEntry> postedEntries = getPostedEntriesInRange(startDate, endDate);

        // Filter lines belonging to this account, sorted by entry date then entry id
        record LineWithEntry(AccJournalEntry entry, AccJournalEntryLine line) {}

        List<LineWithEntry> relevant = postedEntries.stream()
                .flatMap(entry -> entry.getLines().stream()
                        .filter(l -> accountId.equals(l.getAccountId()))
                        .map(l -> new LineWithEntry(entry, l)))
                .sorted(Comparator.comparing((LineWithEntry lwe) -> lwe.entry().getEntryDate())
                        .thenComparing(lwe -> lwe.entry().getId()))
                .toList();

        boolean isDebitNature = account.getNature() == AccAccountNature.DEBIT;
        BigDecimal running = BigDecimal.ZERO;
        List<LedgerEntryDto> result = new ArrayList<>();

        for (LineWithEntry lwe : relevant) {
            BigDecimal dr = safeVal(lwe.line().getDebit());
            BigDecimal cr = safeVal(lwe.line().getCredit());
            if (isDebitNature) {
                running = running.add(dr).subtract(cr);
            } else {
                running = running.add(cr).subtract(dr);
            }
            result.add(new LedgerEntryDto(
                    lwe.entry().getEntryDate(),
                    lwe.entry().getReference(),
                    lwe.entry().getDescription(),
                    lwe.line().getDescription(),
                    dr,
                    cr,
                    running
            ));
        }

        return result;
    }

    // -------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------

    private List<AccJournalEntry> getPostedEntriesInRange(LocalDate startDate, LocalDate endDate) {
        LocalDate from = startDate != null ? startDate : LocalDate.of(1900, 1, 1);
        LocalDate to = endDate != null ? endDate : LocalDate.now();
        return entryRepository.findByStatusAndEntryDateBetweenOrderByEntryDateDesc(
                AccEntryStatus.POSTED, from, to);
    }

    private Map<Long, BigDecimal[]> aggregateLines(List<AccJournalEntry> entries) {
        Map<Long, BigDecimal[]> totals = new HashMap<>();
        for (AccJournalEntry entry : entries) {
            for (AccJournalEntryLine line : entry.getLines()) {
                if (line.getAccountId() == null) continue;
                totals.computeIfAbsent(line.getAccountId(), k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                BigDecimal[] acc = totals.get(line.getAccountId());
                acc[0] = acc[0].add(safeVal(line.getDebit()));
                acc[1] = acc[1].add(safeVal(line.getCredit()));
            }
        }
        return totals;
    }

    private BigDecimal safeVal(BigDecimal val) {
        return val != null ? val : BigDecimal.ZERO;
    }

    private String formatPeriod(LocalDate start, LocalDate end) {
        if (start == null && end == null) return "All periods";
        if (start == null) return "Up to " + end;
        if (end == null) return "From " + start;
        return start + " to " + end;
    }
}
