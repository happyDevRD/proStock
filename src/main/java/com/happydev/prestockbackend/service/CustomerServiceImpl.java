package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.dto.CustomerDto;
import com.happydev.prestockbackend.dto.QuickCustomerRequest;
import com.happydev.prestockbackend.entity.Customer;
import com.happydev.prestockbackend.exception.ResourceNotFoundException;
import com.happydev.prestockbackend.mapper.CustomerMapper;
import com.happydev.prestockbackend.repository.CustomerRepository;
import com.happydev.prestockbackend.util.SecurityAuditUtils;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
@Transactional
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerMapper customerMapper;
    private final AuditService auditService;
    private final PasswordEncoder passwordEncoder;

    public CustomerServiceImpl(
            CustomerRepository customerRepository,
            CustomerMapper customerMapper,
            AuditService auditService,
            PasswordEncoder passwordEncoder
    ) {
        this.customerRepository = customerRepository;
        this.customerMapper = customerMapper;
        this.auditService = auditService;
        this.passwordEncoder = passwordEncoder;
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
        return createCustomer(customerDto, null);
    }

    @Override
    public CustomerDto createCustomer(@NonNull CustomerDto customerDto, @Nullable String idempotencyKey) {
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        if (normalizedKey != null) {
            Optional<Customer> existing = customerRepository.findByIdempotencyKey(normalizedKey);
            if (existing.isPresent()) {
                return customerMapper.toDto(existing.get());
            }
        }

        //Validar que no exista ese email
        if(customerRepository.existsByEmail(customerDto.getEmail())){
            throw new IllegalArgumentException("Email already exists: " + customerDto.getEmail()); // Otra excepción personalizada
        }
        Customer customer = customerMapper.toEntity(customerDto);
        if (normalizedKey != null) {
            customer.setIdempotencyKey(normalizedKey);
        }
        Customer savedCustomer;
        try {
            savedCustomer = customerRepository.save(Objects.requireNonNull(customer));
        } catch (DataIntegrityViolationException ex) {
            if (normalizedKey != null) {
                Optional<Customer> raced = customerRepository.findByIdempotencyKey(normalizedKey);
                if (raced.isPresent()) {
                    return customerMapper.toDto(raced.get());
                }
            }
            throw ex;
        }
        CustomerDto dto = customerMapper.toDto(savedCustomer);
        auditService.record(
                SecurityAuditUtils.currentUsernameOrNull(),
                "CUSTOMER_CREATED",
                "Customer",
                dto.getId(),
                Map.of("email", dto.getEmail(), "name", dto.getFirstName() + " " + dto.getLastName())
        );
        return dto;
    }

    @Nullable
    private static String normalizeIdempotencyKey(@Nullable String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        String trimmed = idempotencyKey.trim();
        if (trimmed.length() > 64) {
            return trimmed.substring(0, 64);
        }
        return trimmed;
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
        CustomerDto dto = customerMapper.toDto(updatedCustomer);
        auditService.record(
                SecurityAuditUtils.currentUsernameOrNull(),
                "CUSTOMER_UPDATED",
                "Customer",
                id,
                Map.of("email", dto.getEmail())
        );
        return dto;
    }

    @Override
    public CustomerDto createQuickCustomer(@NonNull QuickCustomerRequest request) {
        String[] parts = request.getName().trim().split("\\s+", 2);
        Customer customer = new Customer();
        customer.setFirstName(parts[0]);
        customer.setLastName(parts.length > 1 ? parts[1] : "-");
        customer.setPhoneNumber(request.getPhoneNumber());
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            customer.setEmail(request.getEmail().trim());
        }
        Customer saved = customerRepository.save(customer);
        auditService.record(
                SecurityAuditUtils.currentUsernameOrNull(),
                "CUSTOMER_CREATED",
                "Customer",
                saved.getId(),
                Map.of("name", saved.getFirstName() + " " + saved.getLastName(), "quick", "true")
        );
        return customerMapper.toDto(saved);
    }

    @Override
    public CustomerDto setPortalCredentials(@NonNull Long id, @NonNull String rawPassword, boolean enabled) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", id));
        if (customer.getEmail() == null || customer.getEmail().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El cliente necesita un email válido para acceder al portal.");
        }
        customer.setPortalEnabled(enabled);
        customer.setPortalPassword(passwordEncoder.encode(rawPassword));
        Customer saved = customerRepository.save(customer);
        return customerMapper.toDto(saved);
    }

    @Override
    public void deleteCustomer(@NonNull Long id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", id));
        String email = customer.getEmail() != null ? customer.getEmail() : "";
        customerRepository.delete(Objects.requireNonNull(customer));
        auditService.record(
                SecurityAuditUtils.currentUsernameOrNull(),
                "CUSTOMER_DELETED",
                "Customer",
                id,
                Map.of("email", email)
        );
    }
}