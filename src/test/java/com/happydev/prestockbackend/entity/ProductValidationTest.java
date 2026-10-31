package com.happydev.prestockbackend.entity;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Una venta offline ya entregada se completa aunque deje el stock negativo; si la entidad lo
 * prohibiera, Hibernate rechazaría el guardado y la venta quedaría en PENDING para siempre.
 */
class ProductValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void negativeStockIsAllowedOnTheEntity() {
        Set<ConstraintViolation<Product>> violations = validator.validateProperty(product(-1), "stock");
        assertThat(violations).isEmpty();
    }

    @Test
    void stockIsStillRequired() {
        Set<ConstraintViolation<Product>> violations = validator.validateProperty(product(null), "stock");
        assertThat(violations).isNotEmpty();
    }

    private static Product product(Integer stock) {
        Product product = new Product();
        product.setStock(stock);
        return product;
    }
}
