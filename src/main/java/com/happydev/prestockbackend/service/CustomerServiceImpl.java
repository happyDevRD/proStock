package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.dto.CustomerDto;
import com.happydev.prestockbackend.entity.Customer;
import com.happydev.prestockbackend.exception.ResourceNotFoundException;
import com.happydev.prestockbackend.mapper.CustomerMapper;
import com.happydev.prestockbackend.repository.CustomerRepository;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@Transactional
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;

    private final CustomerMapper customerMapper;

    public CustomerServiceImpl(CustomerRepository customerRepository, CustomerMapper customerMapper) {
        this.customerRepository = customerRepository;
        this.customerMapper = customerMapper;
    }

    @Override
    public List<CustomerDto> findAllCustomers() {
        return customerMapper.toDtoList(customerRepository.findAll());
    }

    @Override
    public Page<CustomerDto> findAllCustomers(@NonNull Pageable pageable) {
        Page<Customer> customers = customerRepository.findAll(pageable);
        return customers.map(customerMapper::toDto);
    }

    @Override
    public Optional<CustomerDto> findCustomerById(@NonNull Long id) {
        return customerRepository.findById(id).map(customerMapper::toDto);
    }

    @Override
    public CustomerDto createCustomer(@NonNull CustomerDto customerDto) {
        //Validar que no exista ese email
        if(customerRepository.existsByEmail(customerDto.getEmail())){
            throw new IllegalArgumentException("Email already exists: " + customerDto.getEmail()); // Otra excepción personalizada
        }
        Customer customer = customerMapper.toEntity(customerDto);
        Customer savedCustomer = customerRepository.save(Objects.requireNonNull(customer));
        return customerMapper.toDto(savedCustomer);
    }

    @Override
    public CustomerDto updateCustomer(@NonNull Long id, @NonNull CustomerDto customerDto) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", id));

        // Validación de email único (si se cambia el email)
        if (customerDto.getEmail() != null && !customerDto.getEmail().equals(customer.getEmail())) {
            if (customerRepository.existsByEmail(customerDto.getEmail())) {
                throw new IllegalArgumentException("Email already exists: " + customerDto.getEmail());
            }
            customer.setEmail(customerDto.getEmail());
        }

        //Actualizar los campos.
        customer.setFirstName(customerDto.getFirstName());
        customer.setLastName(customerDto.getLastName());
        customer.setPhoneNumber(customerDto.getPhoneNumber());
        customer.setAddress(customerDto.getAddress());
        customer.setRncCedula(customerDto.getRncCedula());
        customer.setTipoIdentificacion(customerDto.getTipoIdentificacion());

        Customer updatedCustomer = customerRepository.save(customer);
        return customerMapper.toDto(updatedCustomer);
    }

    @Override
    public void deleteCustomer(@NonNull Long id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", id));
        customerRepository.delete(Objects.requireNonNull(customer));
    }
}