package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.dto.CreateLocationRequest;
import com.happydev.prestockbackend.dto.LocationDto;
import com.happydev.prestockbackend.dto.LocationStockItemDto;

import java.util.List;

public interface LocationService {

    List<LocationDto> findAll(boolean activeOnly);

    LocationDto findById(Long id);

    LocationDto create(CreateLocationRequest request);

    LocationDto update(Long id, CreateLocationRequest request);

    void toggleActive(Long id);

    List<LocationStockItemDto> getStockForLocation(Long locationId);
}
