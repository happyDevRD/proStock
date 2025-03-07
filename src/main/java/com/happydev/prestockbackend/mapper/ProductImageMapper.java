package com.happydev.prestockbackend.mapper;

import com.happydev.prestockbackend.dto.ProductImageDto;
import com.happydev.prestockbackend.entity.ProductImage;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ProductImageMapper {

    ProductImageMapper INSTANCE = Mappers.getMapper(ProductImageMapper.class);

    @Mapping(target = "product", ignore = true) // Ignoramos product
    ProductImage toEntity(ProductImageDto productImageDto);

    ProductImageDto toDto(ProductImage productImage);

    List<ProductImageDto> toDtoList(List<ProductImage> productImages);
    List<ProductImage> toEntityList(List<ProductImageDto> productImageDtos);
}
