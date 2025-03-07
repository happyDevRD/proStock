package com.happydev.prestockbackend.controller;

import com.happydev.prestockbackend.entity.Supplier;
import com.happydev.prestockbackend.exception.ResourceNotFoundException;
import com.happydev.prestockbackend.service.SupplierService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/suppliers")
@CrossOrigin(origins = "*") // O la URL de tu frontend
public class SupplierController {

    private final SupplierService supplierService;

    public SupplierController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    @GetMapping
    public ResponseEntity<List<Supplier>> getAllSuppliers() {
        List<Supplier> suppliers = supplierService.findAllSuppliers();
        return new ResponseEntity<>(suppliers, HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Supplier> getSupplierById(@PathVariable Long id) {
        return supplierService.findSupplierById(id)
                .map(supplier -> new ResponseEntity<>(supplier, HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @PostMapping
    public ResponseEntity<Supplier> createSupplier(@RequestBody Supplier supplier) {
        Supplier savedSupplier = supplierService.saveSupplier(supplier);
        return new ResponseEntity<>(savedSupplier, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Supplier> updateSupplier(@PathVariable Long id, @RequestBody Supplier supplierDetails) {
        try{
            Supplier updatedSupplier = supplierService.updateSupplier(id, supplierDetails);
            return new ResponseEntity<>(updatedSupplier,HttpStatus.OK);
        } catch (ResourceNotFoundException e){
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSupplier(@PathVariable Long id) {
        try{
            supplierService.deleteSupplier(id);
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } catch(ResourceNotFoundException e){
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }
}