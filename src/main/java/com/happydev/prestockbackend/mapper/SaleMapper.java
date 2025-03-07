package com.happydev.prestockbackend.mapper;

import com.happydev.prestockbackend.dto.SaleDto;
import com.happydev.prestockbackend.dto.SaleItemDto;
import com.happydev.prestockbackend.entity.Sale;
import com.happydev.prestockbackend.entity.SaleItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper(componentModel = "spring")
public interface SaleMapper {

    SaleMapper INSTANCE = Mappers.getMapper(SaleMapper.class);

    @Mapping(source = "customerId", target = "customer.id") // Mapeo de customerId a customer.id
    Sale toEntity(SaleDto saleDto);

    @Mapping(target = "customerId", source = "customer.id") // Mapeo de customer.id a customerId
    SaleDto toDto(Sale sale);

    List<SaleDto> toDtoList(List<Sale> sales);

    // Mappings para SaleItem (si los necesitas)
    SaleItem toItemEntity(SaleItemDto saleItemDto);
    SaleItemDto toItemDto(SaleItem saleItem);
    List<SaleItemDto> toItemDtoList(List<SaleItem> saleItems);
    List<SaleItem> toItemEntityList(List<SaleItemDto> saleItemDtos);
}
