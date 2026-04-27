package com.happydev.prestockbackend.mapper;

import com.happydev.prestockbackend.dto.ProductImageDto;
import com.happydev.prestockbackend.entity.ProductImage;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ProductImageMapper {
    @Mapping(target = "product", ignore = true)
    @Mapping(target = "contentType", ignore = true)
    @Mapping(source = "url", target = "fileName")
    ProductImage toEntity(ProductImageDto productImageDto);

    @Mapping(source = "fileName", target = "url")
    ProductImageDto toDto(ProductImage productImage);

    List<ProductImageDto> toDtoList(List<ProductImage> productImages);
    List<ProductImage> toEntityList(List<ProductImageDto> productImageDtos);
}
