package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.dto.SaleDto;
import com.happydev.prestockbackend.dto.SalePaymentDto;
import com.happydev.prestockbackend.dto.SaleSummaryDto;
import com.happydev.prestockbackend.entity.PaymentMethod;
import com.happydev.prestockbackend.entity.SaleStatus;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
            LocalDate dateTo,
            @Nullable SaleStatus statusFilter
    );
    Optional<SaleDto> findSaleByNcf(@NonNull String ncf);
    Optional<SaleDto> findSaleById(@NonNull Long id);
    SaleDto createSale(@NonNull SaleDto saleDto);

    SaleDto createSale(@NonNull SaleDto saleDto, @Nullable String idempotencyKey);
    SaleDto updateSale(@NonNull Long id, @NonNull SaleDto saleDto); //Para cambiar datos o estado.
    void deleteSale(@NonNull Long id);
    SaleDto completeSale(@NonNull Long id, @Nullable String actorUsername);

    SalePaymentDto addPayment(@NonNull Long saleId,
                              @NonNull BigDecimal amount,
                              @NonNull PaymentMethod paymentMethod,
                              @Nullable String notes,
                              @Nullable String actorUsername);

    List<SalePaymentDto> getPaymentsForSale(@NonNull Long saleId);

    SaleDto voidPayment(@NonNull Long saleId, @NonNull Long paymentId, @Nullable String actorUsername);

    SaleSummaryDto getSalesSummary(
            @Nullable LocalDateTime startDate,
            @Nullable LocalDateTime endDate,
            @Nullable SaleStatus statusFilter
    );

    List<SaleDto> findSalesForExport(
            @Nullable LocalDateTime startDate,
            @Nullable LocalDateTime endDate,
            @Nullable SaleStatus status
    );

    SaleDto linkToServiceOrder(@NonNull Long saleId, @NonNull Long serviceOrderId);

    SaleDto unlinkFromServiceOrder(@NonNull Long saleId);

    List<SaleDto> findByServiceOrderId(@NonNull Long serviceOrderId);
}
