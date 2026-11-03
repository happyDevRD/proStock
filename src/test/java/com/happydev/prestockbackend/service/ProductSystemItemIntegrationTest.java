package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.dto.ProductDto;
import com.happydev.prestockbackend.entity.Category;
import com.happydev.prestockbackend.entity.IndicadorFacturacion;
import com.happydev.prestockbackend.entity.Product;
import com.happydev.prestockbackend.entity.ProductStatus;
import com.happydev.prestockbackend.entity.Supplier;
import com.happydev.prestockbackend.entity.TipoBienServicio;
import com.happydev.prestockbackend.repository.CategoryRepository;
import com.happydev.prestockbackend.repository.ProductRepository;
import com.happydev.prestockbackend.repository.SupplierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Los productos internos (anticipo/saldo de órdenes) no salen en el POS ni en el inventario. */
@SpringBootTest
@Transactional
class ProductSystemItemIntegrationTest {

    @Autowired private ProductService productService;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private SupplierRepository supplierRepository;

    @BeforeEach
    void seed() {
        Category category = new Category();
        category.setName("Cat prueba system_item");
        category = categoryRepository.save(category);
        Supplier supplier = new Supplier();
        supplier.setName("Prov prueba system_item");
        supplier = supplierRepository.save(supplier);

        productRepository.save(product("TEST-SESION-ORD", "Sesión de orden de estudio", false, category, supplier));
        productRepository.save(product("TEST-SYS-ANT", "Anticipo de orden de prueba", true, category, supplier));
    }

    @Test
    void listadoYBusqueda_ocultanLosProductosDelSistemaPorDefecto() {
        PageRequest page = PageRequest.of(0, 500);

        assertThat(skus(productService.findAllProducts(page, false).getContent()))
                .contains("TEST-SESION-ORD").doesNotContain("TEST-SYS-ANT");
        assertThat(skus(productService.searchProducts("de orden", page, false).getContent()))
                .containsExactly("TEST-SESION-ORD");

        List<ProductDto> conSistema = productService.searchProducts("de orden", page, true).getContent();
        assertThat(skus(conSistema)).containsExactlyInAnyOrder("TEST-SESION-ORD", "TEST-SYS-ANT");
        assertThat(conSistema).filteredOn(p -> "TEST-SYS-ANT".equals(p.getSku()))
                .extracting(ProductDto::getSystemItem).containsExactly(true);
        assertThat(skus(productService.findAllProducts(page, true).getContent())).contains("TEST-SYS-ANT");
    }

    @Test
    void elClienteNoPuedeMarcarNiDesmarcarSystemItem() {
        Product sys = productRepository.findBySku("TEST-SYS-ANT").orElseThrow();
        ProductDto dto = productService.findProductById(sys.getId()).orElseThrow();
        dto.setSystemItem(false);
        dto.setExpirationDate(null);
        productService.updateProduct(sys.getId(), dto);

        assertThat(productRepository.findBySku("TEST-SYS-ANT").orElseThrow().isSystemItem()).isTrue();
    }

    private static List<String> skus(List<ProductDto> products) {
        return products.stream().map(ProductDto::getSku).toList();
    }

    private static Product product(String sku, String name, boolean system, Category category, Supplier supplier) {
        Product p = new Product();
        p.setSku(sku);
        p.setName(name);
        p.setCategory(category);
        p.setSupplier(supplier);
        p.setCostPrice(new BigDecimal("0.01"));
        p.setSellingPrice(new BigDecimal("0.01"));
        p.setStock(0);
        p.setMinStock(0);
        p.setForSale(false);
        p.setSystemItem(system);
        p.setIndicadorFacturacion(IndicadorFacturacion.ITBIS_18);
        p.setTipoBienServicio(TipoBienServicio.SERVICIO);
        p.setUnidadMedida(58);
        p.setStatus(ProductStatus.ACTIVE);
        p.setTaxRate(new BigDecimal("18"));
        p.setImages(new java.util.ArrayList<>());
        return p;
    }
}
