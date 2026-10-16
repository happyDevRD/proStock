package com.happydev.prestockbackend.controller;

import com.happydev.prestockbackend.security.Authz;

import com.happydev.prestockbackend.dto.CustomerDto;
import com.happydev.prestockbackend.dto.QuickCustomerRequest;
import com.happydev.prestockbackend.service.CustomerService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @PreAuthorize(Authz.CUSTOMER_LIST)
    @GetMapping
    public ResponseEntity<List<CustomerDto>> getAllCustomers() {
        List<CustomerDto> customers = customerService.findAllCustomers();
        return new ResponseEntity<>(customers, HttpStatus.OK);
    }

    @PreAuthorize(Authz.CUSTOMER_READ)
    @GetMapping("/paginated")
    public ResponseEntity<Page<CustomerDto>> getAllCustomers(@NonNull Pageable pageable) {
        Page<CustomerDto> customers = customerService.findAllCustomers(pageable);
        return new ResponseEntity<>(customers, HttpStatus.OK);
    }

    @PreAuthorize(Authz.CUSTOMER_READ)
    @GetMapping("/{id}")
    public ResponseEntity<CustomerDto> getCustomerById(@PathVariable @NonNull Long id) {
        return customerService.findCustomerById(id)
                .map(customer -> new ResponseEntity<>(customer, HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @PreAuthorize(Authz.CUSTOMER_CREATE)
    @PostMapping
    public ResponseEntity<CustomerDto> createCustomer(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody @NonNull CustomerDto customerDto
    ) {
        CustomerDto savedCustomer = customerService.createCustomer(customerDto, idempotencyKey);
        return new ResponseEntity<>(savedCustomer, HttpStatus.CREATED);
    }

    @PreAuthorize(Authz.CUSTOMER_EDIT)
    @PutMapping("/{id}")
    public ResponseEntity<CustomerDto> updateCustomer(@PathVariable @NonNull Long id, @Valid @RequestBody @NonNull CustomerDto customerDto) {
        CustomerDto updatedCustomer = customerService.updateCustomer(id, customerDto);
        return new ResponseEntity<>(updatedCustomer, HttpStatus.OK);
    }

    @PreAuthorize(Authz.CUSTOMER_QUICK)
    @PostMapping("/quick")
    public ResponseEntity<CustomerDto> createQuickCustomer(@Valid @RequestBody @NonNull QuickCustomerRequest request) {
        CustomerDto saved = customerService.createQuickCustomer(request);
        return new ResponseEntity<>(saved, HttpStatus.CREATED);
    }

    @PreAuthorize(Authz.CUSTOMER_EDIT)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCustomer(@PathVariable @NonNull Long id) {
        customerService.deleteCustomer(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    /** Habilitar acceso al portal para un cliente (ADMIN/GESTOR). */
    @PutMapping("/{id}/portal-credentials")
    @PreAuthorize("hasAnyAuthority('ROLE_GESTOR', 'portal.manage')")
    public ResponseEntity<CustomerDto> setPortalCredentials(
            @PathVariable @NonNull Long id,
            @Valid @RequestBody PortalCredentialRequest request
    ) {
        CustomerDto updated = customerService.setPortalCredentials(id, request.password(), request.enabled());
        return new ResponseEntity<>(updated, HttpStatus.OK);
    }

    public record PortalCredentialRequest(
            @NotBlank @Size(min = 6) String password,
            boolean enabled
    ) {}
}