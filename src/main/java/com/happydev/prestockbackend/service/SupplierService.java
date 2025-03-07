package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.entity.Supplier;

import java.util.List;
import java.util.Optional;

public interface SupplierService {
    List<Supplier> findAllSuppliers();
    Optional<Supplier> findSupplierById(Long id);
    Supplier saveSupplier(Supplier supplier);
    Supplier updateSupplier(Long id, Supplier supplierDetails);
    void deleteSupplier(Long id);
}
