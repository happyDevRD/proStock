package com.happydev.prestockbackend.mapper;

import com.happydev.prestockbackend.dto.CustomerDto;
import com.happydev.prestockbackend.entity.Customer;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import java.util.List;

@Mapper(componentModel = "spring")
public interface CustomerMapper {

    CustomerMapper INSTANCE = Mappers.getMapper(CustomerMapper.class);

    Customer toEntity(CustomerDto customerDto);
    CustomerDto toDto(Customer customer);
    List<CustomerDto> toDtoList(List<Customer> customers);
}
