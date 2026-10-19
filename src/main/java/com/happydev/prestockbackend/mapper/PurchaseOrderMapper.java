package com.happydev.prestockbackend.mapper;

import com.happydev.prestockbackend.dto.PurchaseOrderDto;
import com.happydev.prestockbackend.dto.PurchaseOrderItemDto;
import com.happydev.prestockbackend.entity.PurchaseOrder;
import com.happydev.prestockbackend.entity.PurchaseOrderItem;
import com.happydev.prestockbackend.service.PurchaseOrderAmounts;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PurchaseOrderMapper {

    @Mappings({
            @Mapping(source = "supplierId", target = "supplier.id"),
            @Mapping(target = "paidAmount", ignore = true),
            @Mapping(target = "accountedReceivedNet", ignore = true),
    })
    PurchaseOrder toEntity(PurchaseOrderDto purchaseOrderDto);

    @Mappings({
            @Mapping(target = "supplierId", source = "supplier.id"),
            @Mapping(target = "total", source = "total"),
            @Mapping(target = "paidAmount", source = "paidAmount"),
            @Mapping(target = "orderValue", ignore = true),
            @Mapping(target = "receivedValue", ignore = true),
            @Mapping(target = "balanceDue", ignore = true),
            @Mapping(target = "remainingUnits", ignore = true),
    })
    PurchaseOrderDto toDto(PurchaseOrder purchaseOrder);

    /** Importes calculados con las mismas reglas que el servicio (PurchaseOrderAmounts). */
    @AfterMapping
    default void fillAmounts(PurchaseOrder purchaseOrder, @MappingTarget PurchaseOrderDto dto) {
        dto.setOrderValue(PurchaseOrderAmounts.orderValue(purchaseOrder));
        dto.setReceivedValue(PurchaseOrderAmounts.receivedValue(purchaseOrder));
        dto.setBalanceDue(PurchaseOrderAmounts.balanceDue(purchaseOrder));
        dto.setRemainingUnits(PurchaseOrderAmounts.remainingUnits(purchaseOrder));
    }

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
