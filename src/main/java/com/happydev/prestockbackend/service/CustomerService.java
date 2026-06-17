package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.dto.CustomerDto;
import com.happydev.prestockbackend.dto.QuickCustomerRequest;
import org.springframework.lang.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;

import java.util.List;
import java.util.Optional;

public interface CustomerService {
    List<CustomerDto> findAllCustomers();
    Page<CustomerDto> findAllCustomers(@NonNull Pageable pageable);
    Optional<CustomerDto> findCustomerById(@NonNull Long id);
    CustomerDto createCustomer(@NonNull CustomerDto customerDto);

    CustomerDto createCustomer(@NonNull CustomerDto customerDto, @Nullable String idempotencyKey);
    CustomerDto updateCustomer(@NonNull Long id, @NonNull CustomerDto customerDto);
    void deleteCustomer(@NonNull Long id);

    CustomerDto createQuickCustomer(@NonNull QuickCustomerRequest request);

    CustomerDto setPortalCredentials(@NonNull Long id, @NonNull String rawPassword, boolean enabled);
}
