package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.dto.CustomerDto;
import org.springframework.lang.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

public interface CustomerService {
    List<CustomerDto> findAllCustomers();
    Page<CustomerDto> findAllCustomers(@NonNull Pageable pageable);
    Optional<CustomerDto> findCustomerById(@NonNull Long id);
    CustomerDto createCustomer(@NonNull CustomerDto customerDto);
    CustomerDto updateCustomer(@NonNull Long id, @NonNull CustomerDto customerDto);
    void deleteCustomer(@NonNull Long id);
}
