package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.dto.CreateStockTransferRequest;
import com.happydev.prestockbackend.dto.StockTransferDto;
import org.springframework.data.domain.Page;

public interface StockTransferService {

    Page<StockTransferDto> list(int page, int size, String status);

    StockTransferDto findById(Long id);

    StockTransferDto create(CreateStockTransferRequest request);

    StockTransferDto complete(Long id);

    StockTransferDto cancel(Long id);

    void delete(Long id);
}
