package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.entity.Supplier;
import org.springframework.lang.NonNull;

import java.util.List;
import java.util.Optional;

public interface SupplierService {
    List<Supplier> findAllSuppliers();
    Optional<Supplier> findSupplierById(@NonNull Long id);
    Supplier saveSupplier(@NonNull Supplier supplier);
    Supplier updateSupplier(@NonNull Long id, @NonNull Supplier supplierDetails);
    void deleteSupplier(@NonNull Long id);
}
