package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.dto.SaleDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface SaleService {
    List<SaleDto> findAllSales();
    Page<SaleDto> findAllSales(Pageable pageable); // Paginación
    Optional<SaleDto> findSaleById(Long id);
    SaleDto createSale(SaleDto saleDto);
    SaleDto updateSale(Long id, SaleDto saleDto); //Para cambiar datos o estado.
    void deleteSale(Long id);
    SaleDto completeSale(Long id); // Método para finalizar una venta y descontar stock
}
