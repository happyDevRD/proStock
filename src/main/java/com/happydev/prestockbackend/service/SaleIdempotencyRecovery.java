package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.entity.Sale;
import com.happydev.prestockbackend.repository.SaleRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * A failed INSERT aborts the whole Postgres transaction, so the idempotency-key
 * lookup after a duplicate-key error can't reuse the same transaction/session —
 * it needs a brand-new one.
 */
@Component
class SaleIdempotencyRecovery {

    private final SaleRepository saleRepository;

    SaleIdempotencyRecovery(SaleRepository saleRepository) {
        this.saleRepository = saleRepository;
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    Optional<Sale> findByIdempotencyKey(String normalizedKey) {
        return saleRepository.findByIdempotencyKey(normalizedKey);
    }
}
