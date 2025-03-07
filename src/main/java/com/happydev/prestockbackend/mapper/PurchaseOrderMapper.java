package com.happydev.prestockbackend.mapper;

import com.happydev.prestockbackend.dto.PurchaseOrderDto;
import com.happydev.prestockbackend.dto.PurchaseOrderItemDto;
import com.happydev.prestockbackend.entity.PurchaseOrder;
import com.happydev.prestockbackend.entity.PurchaseOrderItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PurchaseOrderMapper {

    PurchaseOrderMapper INSTANCE = Mappers.getMapper(PurchaseOrderMapper.class);

    @Mappings({
            @Mapping(source = "supplierId", target = "supplier.id"),
            // No es necesario mapear items aquí, porque lo haremos en el servicio
    })
    PurchaseOrder toEntity(PurchaseOrderDto purchaseOrderDto);

    @Mappings({
            @Mapping(target = "supplierId", source = "supplier.id"),
            // No es necesario mapear items aquí.
    })
    PurchaseOrderDto toDto(PurchaseOrder purchaseOrder);

    List<PurchaseOrderDto> toDtoList(List<PurchaseOrder> purchaseOrders);

    //Para los items
    @Mappings({
            @Mapping(source = "productId", target = "product.id"),
            @Mapping(target = "purchaseOrder", ignore = true) // Ignoramos purchaseOrder aquí
    })
    PurchaseOrderItem toItemEntity(PurchaseOrderItemDto purchaseOrderItemDto);

    @Mappings({
            @Mapping(target = "productId", source = "product.id"),
            //No es necesario el purchaseOrderId
    })
    PurchaseOrderItemDto toItemDto(PurchaseOrderItem purchaseOrderItem);
    List<PurchaseOrderItemDto> toItemDtoList(List<PurchaseOrderItem> items);
    List<PurchaseOrderItem> toItemEntityList(List<PurchaseOrderItemDto> items);

}
