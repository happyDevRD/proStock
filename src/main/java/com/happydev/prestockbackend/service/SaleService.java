package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.dto.SaleDto;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SaleService {
    List<SaleDto> findAllSales();
    Page<SaleDto> findAllSales(@NonNull Pageable pageable); // Paginación
    Page<SaleDto> findInvoices(
            @NonNull Pageable pageable,
            String ncf,
            Long customerId,
            LocalDate dateFrom,
            LocalDate dateTo
    );
    Optional<SaleDto> findSaleByNcf(@NonNull String ncf);
    Optional<SaleDto> findSaleById(@NonNull Long id);
    SaleDto createSale(@NonNull SaleDto saleDto);
    SaleDto updateSale(@NonNull Long id, @NonNull SaleDto saleDto); //Para cambiar datos o estado.
    void deleteSale(@NonNull Long id);
    SaleDto completeSale(@NonNull Long id, @Nullable String actorUsername);
}
