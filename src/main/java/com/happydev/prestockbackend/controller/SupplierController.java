package com.happydev.prestockbackend.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import com.happydev.prestockbackend.security.Authz;

import com.happydev.prestockbackend.entity.Supplier;
import com.happydev.prestockbackend.service.SupplierService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/suppliers")
public class SupplierController {

    private final SupplierService supplierService;

    public SupplierController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    @PreAuthorize(Authz.SUPPLIER_READ)
    @GetMapping
    public ResponseEntity<List<Supplier>> getAllSuppliers() {
        List<Supplier> suppliers = supplierService.findAllSuppliers();
        return new ResponseEntity<>(suppliers, HttpStatus.OK);
    }

    @PreAuthorize(Authz.SUPPLIER_READ)
    @GetMapping("/{id}")
    public ResponseEntity<Supplier> getSupplierById(@PathVariable @NonNull Long id) {
        return supplierService.findSupplierById(id)
                .map(supplier -> new ResponseEntity<>(supplier, HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @PreAuthorize(Authz.SUPPLIER_CREATE)
    @PostMapping
    public ResponseEntity<Supplier> createSupplier(@RequestBody @NonNull Supplier supplier) {
        Supplier savedSupplier = supplierService.saveSupplier(supplier);
        return new ResponseEntity<>(savedSupplier, HttpStatus.CREATED);
    }

    @PreAuthorize(Authz.SUPPLIER_EDIT)
    @PutMapping("/{id}")
    public ResponseEntity<Supplier> updateSupplier(@PathVariable @NonNull Long id, @RequestBody @NonNull Supplier supplierDetails) {
        Supplier updatedSupplier = supplierService.updateSupplier(id, supplierDetails);
        return new ResponseEntity<>(updatedSupplier, HttpStatus.OK);
    }

    @PreAuthorize(Authz.SUPPLIER_EDIT)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSupplier(@PathVariable @NonNull Long id) {
        supplierService.deleteSupplier(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
}