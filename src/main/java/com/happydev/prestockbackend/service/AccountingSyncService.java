package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.dto.AccountSyncPreviewDto;

public interface AccountingSyncService {

    AccountSyncPreviewDto preview();

    int sync(String username);
}
