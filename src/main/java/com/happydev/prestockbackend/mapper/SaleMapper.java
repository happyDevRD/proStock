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
    /** Cliente, serviceOrder y location se asignan en el servicio. */
    @Mapping(target = "customer", ignore = true)
    @Mapping(target = "serviceOrder", ignore = true)
    @Mapping(target = "location", ignore = true)
    Sale toEntity(SaleDto saleDto);

    @Mapping(target = "customerId", source = "customer.id")
    @Mapping(target = "serviceOrderId", source = "serviceOrder.id")
    @Mapping(target = "serviceOrderNumber", source = "serviceOrder.orderNumber")
    @Mapping(target = "locationId", source = "location.id")
    @Mapping(target = "locationName", source = "location.name")
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
