package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.dto.CreateJournalEntryRequest;
import com.happydev.prestockbackend.dto.JournalEntryDto;
import com.happydev.prestockbackend.dto.JournalEntryLineDto;
import com.happydev.prestockbackend.entity.*;
import com.happydev.prestockbackend.repository.AccAccountRepository;
import com.happydev.prestockbackend.repository.AccJournalEntryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class JournalEntryServiceImpl implements JournalEntryService {

    private final AccJournalEntryRepository entryRepository;
    private final AccAccountRepository accountRepository;

    public JournalEntryServiceImpl(AccJournalEntryRepository entryRepository,
                                   AccAccountRepository accountRepository) {
        this.entryRepository = entryRepository;
        this.accountRepository = accountRepository;
    }

    // -------------------------------------------------------
    // Public API
    // -------------------------------------------------------

    @Override
    public List<JournalEntryDto> findAll(String status, LocalDate from, LocalDate to) {
        List<AccJournalEntry> entries;

        if (status != null && !status.isBlank()) {
            AccEntryStatus entryStatus = AccEntryStatus.valueOf(status.toUpperCase());
            if (from != null && to != null) {
                entries = entryRepository
                        .findByStatusAndEntryDateBetweenOrderByEntryDateDesc(entryStatus, from, to);
            } else {
                entries = entryRepository.findByStatusOrderByEntryDateDesc(entryStatus);
            }
        } else {
            entries = entryRepository.findAll();
        }

        Map<Long, AccAccount> accountMap = buildAccountMap();
        return entries.stream().map(e -> toDto(e, accountMap)).toList();
    }

    @Override
    public JournalEntryDto findById(Long id) {
        AccJournalEntry entry = getOrThrow(id);
        return toDto(entry, buildAccountMap());
    }

    @Override
    @Transactional
    public JournalEntryDto create(CreateJournalEntryRequest req, String username) {
        validateBalance(req.lines());

        AccJournalEntry entry = new AccJournalEntry();
        entry.setEntryDate(req.entryDate() != null ? req.entryDate() : LocalDate.now());
        entry.setReference(req.reference());
        entry.setDescription(req.description());
        entry.setEntryType(AccEntryType.MANUAL);
        entry.setStatus(AccEntryStatus.DRAFT);
        entry.setCreatedAt(LocalDateTime.now());
        entry.setCreatedBy(username);

        applyLines(entry, req.lines());

        return toDto(entryRepository.save(entry), buildAccountMap());
    }

    @Override
    @Transactional
    public JournalEntryDto update(Long id, CreateJournalEntryRequest req) {
        AccJournalEntry entry = getOrThrow(id);

        if (entry.getStatus() == AccEntryStatus.POSTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot edit a posted journal entry");
        }

        validateBalance(req.lines());

        entry.setEntryDate(req.entryDate() != null ? req.entryDate() : entry.getEntryDate());
        entry.setReference(req.reference());
        entry.setDescription(req.description());

        entry.getLines().clear();
        applyLines(entry, req.lines());

        return toDto(entryRepository.save(entry), buildAccountMap());
    }

    @Override
    @Transactional
    public JournalEntryDto post(Long id, String username) {
        AccJournalEntry entry = getOrThrow(id);

        if (entry.getStatus() == AccEntryStatus.POSTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Entry is already posted");
        }

        validateLinesBalance(entry.getLines());

        entry.setStatus(AccEntryStatus.POSTED);
        entry.setPostedAt(LocalDateTime.now());
        entry.setPostedBy(username);

        return toDto(entryRepository.save(entry), buildAccountMap());
    }

    @Override
    @Transactional
    public JournalEntryDto reverse(Long id, String username) {
        AccJournalEntry original = getOrThrow(id);

        if (original.getStatus() != AccEntryStatus.POSTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Solo se pueden revertir asientos contabilizados");
        }

        AccJournalEntry reversal = new AccJournalEntry();
        reversal.setEntryDate(LocalDate.now());
        reversal.setReference("REV-" + original.getId());
        reversal.setDescription("Reversión del asiento #" + original.getId()
                + (original.getDescription() != null ? " — " + original.getDescription() : ""));
        reversal.setEntryType(AccEntryType.MANUAL);
        reversal.setStatus(AccEntryStatus.DRAFT);
        reversal.setCreatedAt(LocalDateTime.now());
        reversal.setCreatedBy(username);

        for (AccJournalEntryLine origLine : original.getLines()) {
            AccJournalEntryLine line = new AccJournalEntryLine();
            line.setJournalEntry(reversal);
            line.setAccountId(origLine.getAccountId());
            // Swap debit ↔ credit
            line.setDebit(origLine.getCredit() != null ? origLine.getCredit() : BigDecimal.ZERO);
            line.setCredit(origLine.getDebit() != null ? origLine.getDebit() : BigDecimal.ZERO);
            line.setDescription(origLine.getDescription());
            reversal.getLines().add(line);
        }

        return toDto(entryRepository.save(reversal), buildAccountMap());
    }

    @Override
    @Transactional
    public void delete(Long id) {
        AccJournalEntry entry = getOrThrow(id);
        if (entry.getStatus() == AccEntryStatus.POSTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot delete a posted journal entry");
        }
        entryRepository.delete(entry);
    }

    // -------------------------------------------------------
    // Helpers
    // -------------------------------------------------------

    private AccJournalEntry getOrThrow(Long id) {
        return entryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Journal entry not found: " + id));
    }

    private void applyLines(AccJournalEntry entry, List<CreateJournalEntryRequest.LineRequest> lineRequests) {
        if (lineRequests == null) return;
        for (CreateJournalEntryRequest.LineRequest lr : lineRequests) {
            AccJournalEntryLine line = new AccJournalEntryLine();
            line.setJournalEntry(entry);
            line.setAccountId(lr.accountId());
            line.setDebit(lr.debit() != null ? lr.debit() : BigDecimal.ZERO);
            line.setCredit(lr.credit() != null ? lr.credit() : BigDecimal.ZERO);
            line.setDescription(lr.description());
            entry.getLines().add(line);
        }
    }

    private void validateBalance(List<CreateJournalEntryRequest.LineRequest> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Journal entry must have at least one line");
        }
        BigDecimal totalDebit = lines.stream()
                .map(l -> l.debit() != null ? l.debit() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredit = lines.stream()
                .map(l -> l.credit() != null ? l.credit() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalDebit.compareTo(totalCredit) != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Journal entry is not balanced: debits=" + totalDebit + " credits=" + totalCredit);
        }
    }

    private void validateLinesBalance(List<AccJournalEntryLine> lines) {
        BigDecimal totalDebit = lines.stream()
                .map(l -> l.getDebit() != null ? l.getDebit() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredit = lines.stream()
                .map(l -> l.getCredit() != null ? l.getCredit() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalDebit.compareTo(totalCredit) != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Journal entry is not balanced: debits=" + totalDebit + " credits=" + totalCredit);
        }
    }

    private Map<Long, AccAccount> buildAccountMap() {
        return accountRepository.findAll().stream()
                .collect(Collectors.toMap(AccAccount::getId, Function.identity()));
    }

    public static JournalEntryDto toDto(AccJournalEntry entry, Map<Long, AccAccount> accountMap) {
        List<JournalEntryLineDto> lineDtos = entry.getLines().stream()
                .map(l -> {
                    AccAccount acc = l.getAccountId() != null ? accountMap.get(l.getAccountId()) : null;
                    return new JournalEntryLineDto(
                            l.getId(),
                            l.getAccountId(),
                            acc != null ? acc.getCode() : null,
                            acc != null ? acc.getName() : null,
                            l.getDebit() != null ? l.getDebit() : BigDecimal.ZERO,
                            l.getCredit() != null ? l.getCredit() : BigDecimal.ZERO,
                            l.getDescription()
                    );
                })
                .toList();

        BigDecimal totalDebit = lineDtos.stream()
                .map(JournalEntryLineDto::debit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredit = lineDtos.stream()
                .map(JournalEntryLineDto::credit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new JournalEntryDto(
                entry.getId(),
                entry.getEntryDate(),
                entry.getReference(),
                entry.getDescription(),
                entry.getEntryType() != null ? entry.getEntryType().name() : null,
                entry.getStatus() != null ? entry.getStatus().name() : null,
                entry.getCreatedBy(),
                entry.getCreatedAt(),
                lineDtos,
                totalDebit,
                totalCredit
        );
    }
}
