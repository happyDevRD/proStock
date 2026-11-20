package com.happydev.prestockbackend.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Contra PostgreSQL: «:from IS NULL» sin CAST daba «could not determine data type of parameter $1»
 * y la pestaña Asistencia respondía 500 siempre.
 */
@SpringBootTest
@Transactional
class AttendanceRecordRepositoryTest {

    @Autowired
    private AttendanceRecordRepository repository;

    @Test
    void filtrosPorFecha_conYSinValores_noFallanEnPostgres() {
        LocalDate from = LocalDate.of(2026, 11, 1);
        LocalDate to = LocalDate.of(2026, 11, 30);
        assertThatCode(() -> repository.findAll(from, to)).doesNotThrowAnyException();
        assertThatCode(() -> repository.findAll((LocalDate) null, (LocalDate) null)).doesNotThrowAnyException();
        assertThatCode(() -> repository.findByEmployee(1L, from, null)).doesNotThrowAnyException();
    }
}
