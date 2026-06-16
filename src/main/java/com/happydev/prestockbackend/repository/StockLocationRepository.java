package com.happydev.prestockbackend.repository;

import com.happydev.prestockbackend.entity.StockLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StockLocationRepository extends JpaRepository<StockLocation, Long> {

    Optional<StockLocation> findByProductIdAndLocationId(Long productId, Long locationId);

    List<StockLocation> findByProductId(Long productId);

    @Query("SELECT sl FROM StockLocation sl JOIN FETCH sl.product WHERE sl.location.id = :locationId ORDER BY sl.product.name ASC")
    List<StockLocation> findByLocationIdWithProduct(@Param("locationId") Long locationId);

    @Query("SELECT sl FROM StockLocation sl JOIN FETCH sl.product JOIN FETCH sl.location WHERE sl.location.id = :locationId AND sl.quantity < sl.product.minStock AND sl.product.minStock IS NOT NULL")
    List<StockLocation> findLowStockByLocation(@Param("locationId") Long locationId);
}
