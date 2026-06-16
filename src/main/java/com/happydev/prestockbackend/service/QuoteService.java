package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.dto.CreateQuoteRequest;
import com.happydev.prestockbackend.dto.QuoteConvertResponse;
import com.happydev.prestockbackend.dto.QuoteDto;
import com.happydev.prestockbackend.entity.QuoteStatus;
import org.springframework.data.domain.Page;

public interface QuoteService {

    Page<QuoteDto> list(int page, int size, String status, Long customerId);

    QuoteDto findById(Long id);

    QuoteDto create(CreateQuoteRequest request);

    QuoteDto update(Long id, CreateQuoteRequest request);

    void updateStatus(Long id, QuoteStatus newStatus);

    QuoteConvertResponse convert(Long id);

    void delete(Long id);

    void expireOverdue();
}
