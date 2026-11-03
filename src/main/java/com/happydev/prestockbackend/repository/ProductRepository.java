package com.happydev.prestockbackend.repository;

import com.happydev.prestockbackend.entity.Product;
import com.happydev.prestockbackend.entity.TipoBienServicio;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    @Query("SELECT p FROM Product p WHERE p.stock < p.minStock AND p.tipoBienServicio = :tipoBien")
    List<Product> findProductsBelowMinStock(@Param("tipoBien") TipoBienServicio tipoBien);

    //Para la paginación:
    @Query("SELECT p FROM Product p WHERE p.stock < p.minStock AND p.tipoBienServicio = :tipoBien")
    Page<Product> findProductsBelowMinStock(@Param("tipoBien") TipoBienServicio tipoBien, Pageable pageable);

    boolean existsBySkuIgnoreCase(String sku);

    java.util.Optional<Product> findBySku(String sku);

    boolean existsBySkuIgnoreCaseAndIdNot(String sku, Long id);

    boolean existsByBarcodeIgnoreCase(String barcode);

    boolean existsByBarcodeIgnoreCaseAndIdNot(String barcode, Long id);

    /** Listado para POS/inventario: sin los productos internos del sistema. */
    Page<Product> findBySystemItemFalse(Pageable pageable);

    // CAST: si alguna vez llega null, PostgreSQL no lo tipa como bytea dentro de lower().
    @Query("""
            SELECT p FROM Product p
            WHERE (lower(p.name) LIKE lower(concat('%', CAST(:query AS string), '%'))
               OR lower(p.sku) LIKE lower(concat('%', CAST(:query AS string), '%'))
               OR lower(coalesce(p.barcode, '')) LIKE lower(concat('%', CAST(:query AS string), '%')))
              AND (:includeSystem = true OR p.systemItem = false)
            """)
    Page<Product> searchByQuery(@Param("query") String query,
                                @Param("includeSystem") boolean includeSystem,
                                Pageable pageable);

}
