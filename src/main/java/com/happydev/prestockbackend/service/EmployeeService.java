package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.dto.AttendanceRecordDto;
import com.happydev.prestockbackend.dto.CommissionSummaryDto;
import com.happydev.prestockbackend.dto.EmployeeDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EmployeeService {

    Page<EmployeeDto> findAll(String search, String status, String departamento, Pageable pageable);

    Optional<EmployeeDto> findById(Long id);

    List<EmployeeDto> findAllActive();

    EmployeeDto create(EmployeeDto dto);

    EmployeeDto update(Long id, EmployeeDto dto);

    void delete(Long id);

    // Asistencia
    List<AttendanceRecordDto> findAttendance(Long employeeId, LocalDate from, LocalDate to);

    List<AttendanceRecordDto> findAllAttendance(LocalDate from, LocalDate to);

    AttendanceRecordDto createAttendance(Long employeeId, AttendanceRecordDto dto);

    AttendanceRecordDto updateAttendance(Long attendanceId, AttendanceRecordDto dto);

    void deleteAttendance(Long attendanceId);

    // Comisiones
    CommissionSummaryDto getCommissions(Long employeeId, LocalDate from, LocalDate to);

    List<CommissionSummaryDto> getAllCommissions(LocalDate from, LocalDate to);
}
