package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.dto.CreateLocationRequest;
import com.happydev.prestockbackend.dto.LocationDto;
import com.happydev.prestockbackend.dto.LocationStockItemDto;
import com.happydev.prestockbackend.entity.Location;
import com.happydev.prestockbackend.entity.LocationType;
import com.happydev.prestockbackend.entity.StockLocation;
import com.happydev.prestockbackend.exception.ResourceNotFoundException;
import com.happydev.prestockbackend.repository.LocationRepository;
import com.happydev.prestockbackend.repository.StockLocationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class LocationServiceImpl implements LocationService {

    private final LocationRepository locationRepository;
    private final StockLocationRepository stockLocationRepository;

    public LocationServiceImpl(LocationRepository locationRepository,
                               StockLocationRepository stockLocationRepository) {
        this.locationRepository = locationRepository;
        this.stockLocationRepository = stockLocationRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<LocationDto> findAll(boolean activeOnly) {
        List<Location> locations = activeOnly
                ? locationRepository.findByActiveTrue()
                : locationRepository.findAllByOrderByNameAsc();
        return locations.stream().map(this::toDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public LocationDto findById(Long id) {
        return toDto(locationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Location", "id", id)));
    }

    @Override
    public LocationDto create(CreateLocationRequest request) {
        if (locationRepository.existsByCode(request.code().toUpperCase())) {
            throw new IllegalArgumentException("Ya existe una ubicación con el código: " + request.code());
        }
        Location location = new Location();
        location.setName(request.name());
        location.setCode(request.code().toUpperCase());
        location.setType(request.type() != null ? request.type() : LocationType.BRANCH);
        location.setAddress(request.address());
        location.setPhone(request.phone());
        return toDto(locationRepository.save(location));
    }

    @Override
    public LocationDto update(Long id, CreateLocationRequest request) {
        Location location = locationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Location", "id", id));
        String newCode = request.code().toUpperCase();
        if (!newCode.equals(location.getCode()) && locationRepository.existsByCode(newCode)) {
            throw new IllegalArgumentException("Ya existe una ubicación con el código: " + request.code());
        }
        location.setName(request.name());
        location.setCode(newCode);
        if (request.type() != null) location.setType(request.type());
        location.setAddress(request.address());
        location.setPhone(request.phone());
        return toDto(locationRepository.save(location));
    }

    @Override
    public void toggleActive(Long id) {
        Location location = locationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Location", "id", id));
        if (location.isDefault()) {
            throw new IllegalStateException("No se puede desactivar la ubicación principal.");
        }
        location.setActive(!location.isActive());
        locationRepository.save(location);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LocationStockItemDto> getStockForLocation(Long locationId) {
        locationRepository.findById(locationId)
                .orElseThrow(() -> new ResourceNotFoundException("Location", "id", locationId));
        return stockLocationRepository.findByLocationIdWithProduct(locationId).stream()
                .map(sl -> new LocationStockItemDto(
                        sl.getProduct().getId(),
                        sl.getProduct().getName(),
                        sl.getProduct().getSku(),
                        sl.getQuantity(),
                        sl.getProduct().getMinStock()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<LocationStockItemDto> getLowStockForLocation(Long locationId) {
        locationRepository.findById(locationId)
                .orElseThrow(() -> new ResourceNotFoundException("Location", "id", locationId));
        return stockLocationRepository.findLowStockByLocation(locationId).stream()
                .map(sl -> new LocationStockItemDto(
                        sl.getProduct().getId(),
                        sl.getProduct().getName(),
                        sl.getProduct().getSku(),
                        sl.getQuantity(),
                        sl.getProduct().getMinStock()))
                .toList();
    }

    private LocationDto toDto(Location l) {
        return new LocationDto(l.getId(), l.getName(), l.getCode(), l.getType(),
                l.getAddress(), l.getPhone(), l.isActive(), l.isDefault(), l.getCreatedAt());
    }
}
