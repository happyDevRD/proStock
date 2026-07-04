package com.happydev.prestockbackend.repository;

import com.happydev.prestockbackend.entity.AttendanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {

    @Query("""
            SELECT a FROM AttendanceRecord a
            JOIN FETCH a.employee
            WHERE a.employee.id = :employeeId
              AND (:from IS NULL OR a.fecha >= :from)
              AND (:to IS NULL OR a.fecha <= :to)
            ORDER BY a.fecha DESC
            """)
    List<AttendanceRecord> findByEmployee(
            @Param("employeeId") Long employeeId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query("""
            SELECT a FROM AttendanceRecord a
            JOIN FETCH a.employee
            WHERE (:from IS NULL OR a.fecha >= :from)
              AND (:to IS NULL OR a.fecha <= :to)
            ORDER BY a.fecha DESC, a.employee.nombreCompleto ASC
            """)
    List<AttendanceRecord> findAll(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}
