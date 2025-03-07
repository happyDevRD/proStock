package com.happydev.prestockbackend.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProductImageDto {

    private Long id;

    @NotBlank(message = "La URL de la imagen no puede estar vacía")
    @Size(max = 255, message = "La URL de la imagen no puede exceder los 255 caracteres")
    private String url;
}