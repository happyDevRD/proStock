package com.happydev.prestockbackend.controller;

import com.happydev.prestockbackend.dto.CustomerDto;
import com.happydev.prestockbackend.service.CustomerService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
@CrossOrigin(origins = "*") // Configura CORS adecuadamente en producción
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping
    public ResponseEntity<List<CustomerDto>> getAllCustomers() {
        List<CustomerDto> customers = customerService.findAllCustomers();
        return new ResponseEntity<>(customers, HttpStatus.OK);
    }

    @GetMapping("/paginated")
    public ResponseEntity<Page<CustomerDto>> getAllCustomers(Pageable pageable) {
        Page<CustomerDto> customers = customerService.findAllCustomers(pageable);
        return new ResponseEntity<>(customers, HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CustomerDto> getCustomerById(@PathVariable Long id) {
        return customerService.findCustomerById(id)
                .map(customer -> new ResponseEntity<>(customer, HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @PostMapping
    public ResponseEntity<CustomerDto> createCustomer(@Valid @RequestBody CustomerDto customerDto) {
        CustomerDto savedCustomer = customerService.createCustomer(customerDto);
        return new ResponseEntity<>(savedCustomer, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CustomerDto> updateCustomer(@PathVariable Long id, @Valid @RequestBody CustomerDto customerDto) {
        CustomerDto updatedCustomer = customerService.updateCustomer(id, customerDto);
        return new ResponseEntity<>(updatedCustomer, HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCustomer(@PathVariable Long id) {
        customerService.deleteCustomer(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
}