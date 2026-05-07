package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.entity.Supplier;
import com.happydev.prestockbackend.exception.ResourceNotFoundException;
import com.happydev.prestockbackend.repository.SupplierRepository;
import com.happydev.prestockbackend.util.SecurityAuditUtils;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
@Transactional
public class SupplierServiceImpl implements SupplierService {

    private final SupplierRepository supplierRepository;

    private final AuditService auditService;

    public SupplierServiceImpl(SupplierRepository supplierRepository, AuditService auditService) {
        this.supplierRepository = supplierRepository;
        this.auditService = auditService;
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
        Supplier saved = supplierRepository.save(Objects.requireNonNull(supplier));
        auditService.record(
                SecurityAuditUtils.currentUsernameOrNull(),
                "SUPPLIER_CREATED",
                "Supplier",
                saved.getId(),
                Map.of("name", saved.getName() != null ? saved.getName() : "")
        );
        return saved;
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

        Supplier saved = supplierRepository.save(Objects.requireNonNull(supplier));
        auditService.record(
                SecurityAuditUtils.currentUsernameOrNull(),
                "SUPPLIER_UPDATED",
                "Supplier",
                id,
                Map.of("name", saved.getName() != null ? saved.getName() : "")
        );
        return saved;
    }

    @Override
    public void deleteSupplier(@NonNull Long id) {
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(()-> new ResourceNotFoundException("Supplier", "id", id));
        String name = supplier.getName() != null ? supplier.getName() : "";
        supplierRepository.delete(Objects.requireNonNull(supplier));
        auditService.record(
                SecurityAuditUtils.currentUsernameOrNull(),
                "SUPPLIER_DELETED",
                "Supplier",
                id,
                Map.of("name", name)
        );
    }
}
