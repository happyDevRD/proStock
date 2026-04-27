// ProductMapper.java
package com.happydev.prestockbackend.mapper;

import com.happydev.prestockbackend.dto.ProductDto;
import com.happydev.prestockbackend.dto.ProductImageDto;
import com.happydev.prestockbackend.entity.Product;
import com.happydev.prestockbackend.entity.ProductImage;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ProductMapper {

    @Mapping(source = "categoryId", target = "category.id")
    @Mapping(source = "supplierId", target = "supplier.id")
    @Mapping(source = "forSale", target = "forSale")
    Product toEntity(ProductDto productDto);

    @Mapping(target = "categoryId", source = "category.id")
    @Mapping(target = "supplierId", source = "supplier.id")
    @Mapping(target = "forSale", source = "forSale")
    ProductDto toDto(Product product);

    List<ProductDto> toDtoList(List<Product> products);
    List<Product> toEntityList(List<ProductDto> productDtos);

    @Mapping(source = "url", target = "fileName")
    @Mapping(target = "product", ignore = true)
    @Mapping(target = "contentType", ignore = true)
    ProductImage toImageEntity(ProductImageDto productImageDto);

    @Mapping(source = "fileName", target = "url")
    ProductImageDto toImageDto(ProductImage productImage);
    List<ProductImage> toImageEntityList(List<ProductImageDto> productImageDtos);

    @Mapping(target = "id", ignore = true) //Ignorar ID en la actualización
    @Mapping(target = "category", ignore = true) //Ignorar Category y Supplier
    @Mapping(target = "supplier", ignore = true) //El mapeo se hace por ID
    void updateProductFromDto(ProductDto productDto, @MappingTarget Product product);
}