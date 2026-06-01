package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.dto.AccountDto;

import java.util.List;

public interface AccAccountService {

    List<AccountDto> findAll();

    AccountDto findById(Long id);

    AccountDto create(AccountDto dto);

    AccountDto update(Long id, AccountDto dto);

    void deactivate(Long id);
}
