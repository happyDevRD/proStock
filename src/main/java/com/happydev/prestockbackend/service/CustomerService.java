package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.dto.CustomerDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

public interface CustomerService {
    List<CustomerDto> findAllCustomers();
    Page<CustomerDto> findAllCustomers(Pageable pageable);
    Optional<CustomerDto> findCustomerById(Long id);
    CustomerDto createCustomer(CustomerDto customerDto);
    CustomerDto updateCustomer(Long id, CustomerDto customerDto);
    void deleteCustomer(Long id);
}
