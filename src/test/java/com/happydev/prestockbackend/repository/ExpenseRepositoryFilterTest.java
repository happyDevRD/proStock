package com.happydev.prestockbackend.repository;

import com.happydev.prestockbackend.dto.ExpenseDto;
import com.happydev.prestockbackend.entity.Expense;
import com.happydev.prestockbackend.entity.ExpenseCategory;
import com.happydev.prestockbackend.service.ExpenseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contra PostgreSQL: un parámetro String nulo dentro de LOWER() llegaba como bytea
 * ("function lower(bytea) does not exist") y el listado de gastos sin búsqueda daba 500.
 */
@SpringBootTest
@Transactional
class ExpenseRepositoryFilterTest {

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private ExpenseService expenseService;

    @BeforeEach
    void seed() {
        expenseRepository.save(expense("Pago de luz", ExpenseCategory.UTILITIES));
        expenseRepository.save(expense("Alquiler local", ExpenseCategory.RENT));
    }

    @Test
    void listadoSinBusquedaNiCategoria_noFallaEnPostgres() {
        Page<ExpenseDto> page = expenseService.findAll("", null, PageRequest.of(0, 50));
        assertThat(page.getContent()).extracting(ExpenseDto::getDescription)
                .contains("Pago de luz", "Alquiler local");
    }

    @Test
    void repositorioConDescripcionNula_devuelveTodo() {
        Page<Expense> page = expenseRepository.findFiltered(null, null, PageRequest.of(0, 50));
        assertThat(page.getContent()).extracting(Expense::getDescription)
                .contains("Pago de luz", "Alquiler local");
    }

    @Test
    void busquedaYCategoria_filtran() {
        assertThat(expenseService.findAll("LUZ", null, PageRequest.of(0, 50)).getContent())
                .extracting(ExpenseDto::getDescription).containsExactly("Pago de luz");
        assertThat(expenseService.findAll("", "rent", PageRequest.of(0, 50)).getContent())
                .extracting(ExpenseDto::getDescription).containsExactly("Alquiler local");
    }

    private static Expense expense(String description, ExpenseCategory category) {
        Expense e = new Expense();
        e.setDescription(description);
        e.setAmount(new BigDecimal("1000.00"));
        e.setExpenseDate(LocalDate.now());
        e.setCategory(category);
        return e;
    }
}
