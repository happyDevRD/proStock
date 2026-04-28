package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.entity.Supplier;
import com.happydev.prestockbackend.exception.ResourceNotFoundException;
import com.happydev.prestockbackend.repository.SupplierRepository;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@Transactional
public class SupplierServiceImpl implements SupplierService {

    private final SupplierRepository supplierRepository;

    public SupplierServiceImpl(SupplierRepository supplierRepository) {
        this.supplierRepository = supplierRepository;
    }

    @Override
    public List<Supplier> findAllSuppliers() {
        return supplierRepository.findAll();
    }

    @Override
    public Optional<Supplier> findSupplierById(@NonNull Long id) {
        return supplierRepository.findById(id);
    }

    @Override
    public Supplier saveSupplier(@NonNull Supplier supplier) {
        return supplierRepository.save(Objects.requireNonNull(supplier));
    }

    @Override
    public Supplier updateSupplier(@NonNull Long id, @NonNull Supplier supplierDetails) {
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier", "id", id));

        supplier.setName(supplierDetails.getName());
        supplier.setContactName(supplierDetails.getContactName());
        supplier.setContactEmail(supplierDetails.getContactEmail());
        supplier.setPhone(supplierDetails.getPhone());
        supplier.setAddress(supplierDetails.getAddress());
        // Actualiza otros campos si es necesario.

        return supplierRepository.save(Objects.requireNonNull(supplier));
    }

    @Override
    public void deleteSupplier(@NonNull Long id) {
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(()-> new ResourceNotFoundException("Supplier", "id", id));
        supplierRepository.delete(Objects.requireNonNull(supplier));
    }
}
