package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.dto.AccountDto;
import com.happydev.prestockbackend.entity.AccAccount;
import com.happydev.prestockbackend.entity.AccAccountNature;
import com.happydev.prestockbackend.entity.AccAccountType;
import com.happydev.prestockbackend.repository.AccAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class AccAccountServiceImpl implements AccAccountService {

    private final AccAccountRepository accountRepository;

    public AccAccountServiceImpl(AccAccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    // -------------------------------------------------------
    // Public API
    // -------------------------------------------------------

    @Override
    public List<AccountDto> findAll() {
        return accountRepository.findByActiveTrue()
                .stream()
                .map(AccAccountServiceImpl::toDto)
                .toList();
    }

    @Override
    public AccountDto findById(Long id) {
        return toDto(getOrThrow(id));
    }

    @Override
    @Transactional
    public AccountDto create(AccountDto dto) {
        AccAccount account = new AccAccount();
        applyDto(account, dto);
        account.setCreatedAt(LocalDateTime.now());
        return toDto(accountRepository.save(account));
    }

    @Override
    @Transactional
    public AccountDto update(Long id, AccountDto dto) {
        AccAccount account = getOrThrow(id);
        applyDto(account, dto);
        return toDto(accountRepository.save(account));
    }

    @Override
    @Transactional
    public void deactivate(Long id) {
        AccAccount account = getOrThrow(id);
        account.setActive(false);
        accountRepository.save(account);
    }

    // -------------------------------------------------------
    // Helpers
    // -------------------------------------------------------

    private AccAccount getOrThrow(Long id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Account not found: " + id));
    }

    private void applyDto(AccAccount account, AccountDto dto) {
        account.setCode(dto.code());
        account.setName(dto.name());
        account.setType(AccAccountType.valueOf(dto.type()));
        account.setNature(AccAccountNature.valueOf(dto.nature()));
        account.setParentId(dto.parentId());
        account.setAllowsTransactions(dto.allowsTransactions());
        account.setSystem(dto.isSystem());
        account.setActive(dto.active());
    }

    public static AccountDto toDto(AccAccount a) {
        return new AccountDto(
                a.getId(),
                a.getCode(),
                a.getName(),
                a.getType().name(),
                a.getNature().name(),
                a.getParentId(),
                a.isAllowsTransactions(),
                a.isSystem(),
                a.isActive()
        );
    }
}
