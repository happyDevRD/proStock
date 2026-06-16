package com.happydev.prestockbackend.dto;

import com.happydev.prestockbackend.entity.LocationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateLocationRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 30)  String code,
        LocationType type,
        String address,
        String phone
) {}
