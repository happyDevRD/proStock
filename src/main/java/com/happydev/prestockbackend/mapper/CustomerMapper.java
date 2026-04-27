package com.happydev.prestockbackend.mapper;

import com.happydev.prestockbackend.dto.CustomerDto;
import com.happydev.prestockbackend.entity.Customer;
import org.mapstruct.Mapper;

import java.util.List;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface CustomerMapper {

    Customer toEntity(CustomerDto customerDto);
    CustomerDto toDto(Customer customer);

    default List<CustomerDto> toDtoList(List<Customer> customers) {
        return customers == null ? List.of() : customers.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }
}
