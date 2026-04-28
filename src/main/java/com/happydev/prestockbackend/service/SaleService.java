package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.dto.SaleDto;
import org.springframework.lang.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface SaleService {
    List<SaleDto> findAllSales();
    Page<SaleDto> findAllSales(@NonNull Pageable pageable); // Paginación
    Optional<SaleDto> findSaleById(@NonNull Long id);
    SaleDto createSale(@NonNull SaleDto saleDto);
    SaleDto updateSale(@NonNull Long id, @NonNull SaleDto saleDto); //Para cambiar datos o estado.
    void deleteSale(@NonNull Long id);
    SaleDto completeSale(@NonNull Long id); // Método para finalizar una venta y descontar stock
}
