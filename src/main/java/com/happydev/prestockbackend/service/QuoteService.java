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

    QuoteDto revertToDraft(Long id);

    QuoteConvertResponse convert(Long id);

    /** Crea una orden de servicio con el cliente, el monto y las líneas de la cotización, y la marca convertida. */
    com.happydev.prestockbackend.dto.ServiceOrderDto convertToServiceOrder(Long id, String actor);

    /** Marca la cotización como convertida en la venta indicada (tras cobrarla en el POS). */
    void markConverted(Long id, Long saleId);

    void delete(Long id);

    void expireOverdue();
}
