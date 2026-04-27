package com.happydev.prestockbackend.mapper;

import com.happydev.prestockbackend.dto.SaleDto;
import com.happydev.prestockbackend.dto.SaleItemDto;
import com.happydev.prestockbackend.entity.Sale;
import com.happydev.prestockbackend.entity.SaleItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface SaleMapper {
    @Mapping(source = "customerId", target = "customer.id") // Mapeo de customerId a customer.id
    Sale toEntity(SaleDto saleDto);

    @Mapping(target = "customerId", source = "customer.id") // Mapeo de customer.id a customerId
    SaleDto toDto(Sale sale);

    List<SaleDto> toDtoList(List<Sale> sales);

    @Mapping(target = "sale", ignore = true)
    @Mapping(source = "productId", target = "product.id")
    SaleItem toItemEntity(SaleItemDto saleItemDto);

    @Mapping(target = "productId", source = "product.id")
    SaleItemDto toItemDto(SaleItem saleItem);
    List<SaleItemDto> toItemDtoList(List<SaleItem> saleItems);
    List<SaleItem> toItemEntityList(List<SaleItemDto> saleItemDtos);
}
