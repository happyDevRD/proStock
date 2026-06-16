package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.dto.CreateQuoteRequest;
import com.happydev.prestockbackend.dto.QuoteConvertResponse;
import com.happydev.prestockbackend.dto.QuoteDto;
import com.happydev.prestockbackend.dto.QuoteItemDto;
import com.happydev.prestockbackend.entity.Customer;
import com.happydev.prestockbackend.entity.Product;
import com.happydev.prestockbackend.entity.Quote;
import com.happydev.prestockbackend.entity.QuoteItem;
import com.happydev.prestockbackend.entity.QuoteStatus;
import com.happydev.prestockbackend.exception.ResourceNotFoundException;
import com.happydev.prestockbackend.repository.CustomerRepository;
import com.happydev.prestockbackend.repository.ProductRepository;
import com.happydev.prestockbackend.repository.QuoteRepository;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class QuoteServiceImpl implements QuoteService {

    private final QuoteRepository quoteRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;

    public QuoteServiceImpl(QuoteRepository quoteRepository,
                            CustomerRepository customerRepository,
                            ProductRepository productRepository) {
        this.quoteRepository = quoteRepository;
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
    }

    @Override
    public Page<QuoteDto> list(int page, int size, String status, Long customerId) {
        Pageable pageable = PageRequest.of(page, size);
        if (customerId != null) {
            return quoteRepository.findByCustomerIdOrderByQuoteDateDesc(customerId, pageable)
                    .map(this::mapToDto);
        }
        if (status != null && !status.isBlank()) {
            QuoteStatus qs = QuoteStatus.valueOf(status.toUpperCase());
            return quoteRepository.findByStatusOrderByQuoteDateDesc(qs, pageable)
                    .map(this::mapToDto);
        }
        return quoteRepository.findAllByOrderByQuoteDateDesc(pageable).map(this::mapToDto);
    }

    @Override
    public QuoteDto findById(Long id) {
        Quote quote = quoteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Quote", "id", id));
        return mapToDto(quote);
    }

    @Override
    public QuoteDto create(CreateQuoteRequest request) {
        Quote quote = new Quote();
        quote.setQuoteDate(LocalDateTime.now());
        quote.setStatus(QuoteStatus.DRAFT);
        quote.setExpiryDate(request.expiryDate());
        quote.setNotes(request.notes());
        quote.setCreatedAt(LocalDateTime.now());
        quote.setUpdatedAt(LocalDateTime.now());

        if (request.customerId() != null) {
            Customer customer = customerRepository.findById(request.customerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", request.customerId()));
            quote.setCustomer(customer);
        }

        populateItems(quote, request.items());
        recalculateTotals(quote);

        return mapToDto(quoteRepository.save(quote));
    }

    @Override
    public QuoteDto update(Long id, CreateQuoteRequest request) {
        Quote quote = quoteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Quote", "id", id));

        if (quote.getStatus() != QuoteStatus.DRAFT && quote.getStatus() != QuoteStatus.SENT) {
            throw new IllegalStateException(
                    "Solo se pueden editar cotizaciones en estado DRAFT o SENT. Estado actual: " + quote.getStatus());
        }

        quote.setExpiryDate(request.expiryDate());
        quote.setNotes(request.notes());
        quote.setUpdatedAt(LocalDateTime.now());

        if (request.customerId() != null) {
            Customer customer = customerRepository.findById(request.customerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", request.customerId()));
            quote.setCustomer(customer);
        } else {
            quote.setCustomer(null);
        }

        quote.getItems().clear();
        populateItems(quote, request.items());
        recalculateTotals(quote);

        return mapToDto(quoteRepository.save(quote));
    }

    @Override
    public void updateStatus(Long id, QuoteStatus newStatus) {
        Quote quote = quoteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Quote", "id", id));

        switch (newStatus) {
            case SENT -> {
                if (quote.getStatus() != QuoteStatus.DRAFT) {
                    throw new IllegalStateException("Solo se puede enviar una cotización en estado DRAFT. Estado actual: " + quote.getStatus());
                }
            }
            case ACCEPTED -> {
                if (quote.getStatus() != QuoteStatus.SENT) {
                    throw new IllegalStateException("Solo se puede aceptar una cotización en estado SENT. Estado actual: " + quote.getStatus());
                }
            }
            case CANCELED -> {
                if (quote.getStatus() == QuoteStatus.CONVERTED || quote.getStatus() == QuoteStatus.EXPIRED) {
                    throw new IllegalStateException("No se puede cancelar una cotización en estado " + quote.getStatus());
                }
            }
            default -> throw new IllegalArgumentException("Transición de estado no soportada: " + newStatus);
        }

        quote.setStatus(newStatus);
        quote.setUpdatedAt(LocalDateTime.now());
        quoteRepository.save(quote);
    }

    @Override
    public QuoteConvertResponse convert(Long id) {
        Quote quote = quoteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Quote", "id", id));

        if (quote.getStatus() == QuoteStatus.EXPIRED || quote.getStatus() == QuoteStatus.CANCELED) {
            throw new IllegalStateException("No se puede convertir una cotización en estado " + quote.getStatus());
        }

        quote.setStatus(QuoteStatus.CONVERTED);
        quote.setConvertedSaleId(null);
        quote.setUpdatedAt(LocalDateTime.now());
        quoteRepository.save(quote);

        Long customerId = quote.getCustomer() != null ? quote.getCustomer().getId() : null;

        List<QuoteConvertResponse.QuoteItemForPos> posItems = quote.getItems().stream()
                .map(item -> new QuoteConvertResponse.QuoteItemForPos(
                        item.getProduct() != null ? item.getProduct().getId() : null,
                        item.getQuantity(),
                        item.getUnitPrice()
                ))
                .collect(Collectors.toList());

        return new QuoteConvertResponse(id, customerId, posItems, quote.getMontoTotal());
    }

    @Override
    public void delete(Long id) {
        Quote quote = quoteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Quote", "id", id));

        if (quote.getStatus() != QuoteStatus.DRAFT && quote.getStatus() != QuoteStatus.CANCELED) {
            throw new IllegalStateException(
                    "Solo se pueden eliminar cotizaciones en estado DRAFT o CANCELED. Estado actual: " + quote.getStatus());
        }

        quoteRepository.delete(quote);
    }

    @Override
    @Scheduled(cron = "0 0 * * * *")
    public void expireOverdue() {
        quoteRepository.expireOverdue(LocalDate.now());
    }

    // -----------------------------------------------------------------------

    private void populateItems(Quote quote, List<CreateQuoteRequest.QuoteItemRequest> itemRequests) {
        if (itemRequests == null) return;
        for (CreateQuoteRequest.QuoteItemRequest req : itemRequests) {
            QuoteItem item = new QuoteItem();
            item.setQuote(quote);
            item.setProductName(req.productName());
            item.setUnitPrice(req.unitPrice());
            item.setQuantity(req.quantity());
            item.setDiscount(req.discount() != null ? req.discount() : BigDecimal.ZERO);
            item.setItbisRate(req.itbisRate() != null ? req.itbisRate() : BigDecimal.ZERO);

            if (req.productId() != null) {
                Product product = productRepository.findById(req.productId())
                        .orElseThrow(() -> new ResourceNotFoundException("Product", "id", req.productId()));
                item.setProduct(product);
            }

            // subtotal = (qty * unitPrice - discount)
            BigDecimal lineBase = req.unitPrice()
                    .multiply(BigDecimal.valueOf(req.quantity()))
                    .subtract(item.getDiscount());
            item.setSubtotal(lineBase.max(BigDecimal.ZERO));

            quote.getItems().add(item);
        }
    }

    private void recalculateTotals(Quote quote) {
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal totalItbis = BigDecimal.ZERO;

        for (QuoteItem item : quote.getItems()) {
            BigDecimal lineBase = item.getUnitPrice()
                    .multiply(BigDecimal.valueOf(item.getQuantity()))
                    .subtract(item.getDiscount() != null ? item.getDiscount() : BigDecimal.ZERO);
            lineBase = lineBase.max(BigDecimal.ZERO);

            BigDecimal lineItbis = lineBase.multiply(
                    item.getItbisRate() != null ? item.getItbisRate() : BigDecimal.ZERO);

            subtotal = subtotal.add(lineBase);
            totalItbis = totalItbis.add(lineItbis);
        }

        quote.setSubtotal(subtotal);
        quote.setTotalItbis(totalItbis);
        quote.setMontoTotal(subtotal.add(totalItbis));
    }

    private QuoteDto mapToDto(Quote quote) {
        String customerName = null;
        Long customerId = null;
        if (quote.getCustomer() != null) {
            customerId = quote.getCustomer().getId();
            customerName = quote.getCustomer().getFirstName() + " " + quote.getCustomer().getLastName();
        }

        boolean expired = quote.getExpiryDate() != null
                && quote.getExpiryDate().isBefore(LocalDate.now())
                && (quote.getStatus() == QuoteStatus.DRAFT || quote.getStatus() == QuoteStatus.SENT);

        List<QuoteItemDto> itemDtos = quote.getItems().stream()
                .map(item -> new QuoteItemDto(
                        item.getId(),
                        item.getProduct() != null ? item.getProduct().getId() : null,
                        item.getProductName(),
                        item.getUnitPrice(),
                        item.getQuantity(),
                        item.getDiscount(),
                        item.getItbisRate(),
                        item.getSubtotal()
                ))
                .collect(Collectors.toList());

        return new QuoteDto(
                quote.getId(),
                customerId,
                customerName,
                quote.getQuoteDate(),
                quote.getExpiryDate(),
                quote.getStatus() != null ? quote.getStatus().name() : null,
                quote.getNotes(),
                quote.getSubtotal(),
                quote.getTotalItbis(),
                quote.getDiscountAmount(),
                quote.getMontoTotal(),
                quote.getConvertedSaleId(),
                expired,
                itemDtos,
                quote.getCreatedAt(),
                quote.getUpdatedAt()
        );
    }
}
