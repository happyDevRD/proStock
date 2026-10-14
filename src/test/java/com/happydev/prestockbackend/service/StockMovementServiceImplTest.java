package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.entity.Product;
import com.happydev.prestockbackend.entity.StockMovement;
import com.happydev.prestockbackend.entity.StockMovementType;
import com.happydev.prestockbackend.repository.ProductRepository;
import com.happydev.prestockbackend.repository.StockMovementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockMovementServiceImplTest {

    @Mock
    private StockMovementRepository stockMovementRepository;
    @Mock
    private ProductRepository productRepository;
    @InjectMocks
    private StockMovementServiceImpl service;

    private Product product;

    @BeforeEach
    void setUp() {
        product = new Product();
        product.setId(49L);
        product.setStock(0);
        when(productRepository.findById(49L)).thenReturn(Optional.of(product));
        lenient().when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(i -> i.getArgument(0));
    }

    private StockMovement saleOut(boolean allowNegative) {
        StockMovement movement = new StockMovement();
        movement.setProduct(product);
        movement.setType(StockMovementType.OUT);
        movement.setQuantityChange(-1);
        movement.setAllowNegativeStock(allowNegative);
        return movement;
    }

    @Test
    void outWithoutStock_isRejectedByDefault() {
        assertThrows(IllegalStateException.class, () -> service.createMovement(saleOut(false)));
        verify(productRepository, never()).save(any());
    }

    @Test
    void outWithoutStock_allowedForOfflineSale_leavesNegativeStock() {
        StockMovement saved = service.createMovement(saleOut(true));

        assertEquals(0, saved.getStockBefore());
        assertEquals(-1, saved.getStockAfter());
        assertEquals(-1, product.getStock());
        verify(productRepository).save(product);
    }
}
