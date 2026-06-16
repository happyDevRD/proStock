package com.happydev.prestockbackend.controller;

import com.happydev.prestockbackend.dto.CreateLocationRequest;
import com.happydev.prestockbackend.dto.LocationDto;
import com.happydev.prestockbackend.dto.LocationStockItemDto;
import com.happydev.prestockbackend.service.LocationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/locations")
public class LocationController {

    private final LocationService locationService;

    public LocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_GESTOR', 'ROLE_ADMIN', 'locations.view')")
    public ResponseEntity<List<LocationDto>> list(
            @RequestParam(defaultValue = "false") boolean activeOnly) {
        return ResponseEntity.ok(locationService.findAll(activeOnly));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_GESTOR', 'ROLE_ADMIN', 'locations.view')")
    public ResponseEntity<LocationDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(locationService.findById(id));
    }

    @GetMapping("/{id}/stock")
    @PreAuthorize("hasAnyAuthority('ROLE_GESTOR', 'ROLE_ADMIN', 'locations.view')")
    public ResponseEntity<List<LocationStockItemDto>> getStock(@PathVariable Long id) {
        return ResponseEntity.ok(locationService.getStockForLocation(id));
    }

    @GetMapping("/{id}/low-stock")
    @PreAuthorize("hasAnyAuthority('ROLE_GESTOR', 'ROLE_ADMIN', 'locations.view')")
    public ResponseEntity<List<LocationStockItemDto>> getLowStock(@PathVariable Long id) {
        return ResponseEntity.ok(locationService.getLowStockForLocation(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE_GESTOR', 'ROLE_ADMIN', 'locations.edit')")
    public ResponseEntity<LocationDto> create(@Valid @RequestBody CreateLocationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(locationService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_GESTOR', 'ROLE_ADMIN', 'locations.edit')")
    public ResponseEntity<LocationDto> update(
            @PathVariable Long id,
            @Valid @RequestBody CreateLocationRequest request) {
        return ResponseEntity.ok(locationService.update(id, request));
    }

    @PatchMapping("/{id}/toggle-active")
    @PreAuthorize("hasAnyAuthority('ROLE_GESTOR', 'ROLE_ADMIN', 'locations.edit')")
    public ResponseEntity<Void> toggleActive(@PathVariable Long id) {
        locationService.toggleActive(id);
        return ResponseEntity.noContent().build();
    }
}
