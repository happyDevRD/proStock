package com.happydev.prestockbackend.controller;

import com.happydev.prestockbackend.dto.AttendanceRecordDto;
import com.happydev.prestockbackend.dto.CommissionSummaryDto;
import com.happydev.prestockbackend.dto.EmployeeDto;
import com.happydev.prestockbackend.service.EmployeeService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/employees")
@Tag(name = "Employees", description = "Módulo de empleados, asistencia y comisiones")
public class EmployeeController {

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    // ── CRUD empleados ──────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasAuthority('view.employees')")
    public Page<EmployeeDto> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String departamento) {
        return employeeService.findAll(search, status, departamento,
                PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "nombreCompleto")));
    }

    @GetMapping("/active")
    @PreAuthorize("hasAuthority('view.employees')")
    public List<EmployeeDto> listActive() {
        return employeeService.findAllActive();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('view.employees')")
    public ResponseEntity<EmployeeDto> getById(@PathVariable Long id) {
        return employeeService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('employees.create')")
    public ResponseEntity<EmployeeDto> create(@RequestBody EmployeeDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(employeeService.create(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('employees.edit')")
    public ResponseEntity<EmployeeDto> update(@PathVariable Long id, @RequestBody EmployeeDto dto) {
        return ResponseEntity.ok(employeeService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('employees.delete')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        employeeService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ── Asistencia ──────────────────────────────────────────────────────────

    @GetMapping("/attendance")
    @PreAuthorize("hasAuthority('employees.attendance')")
    public List<AttendanceRecordDto> listAllAttendance(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return employeeService.findAllAttendance(from, to);
    }

    @GetMapping("/{id}/attendance")
    @PreAuthorize("hasAuthority('employees.attendance')")
    public List<AttendanceRecordDto> listAttendance(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return employeeService.findAttendance(id, from, to);
    }

    @PostMapping("/{id}/attendance")
    @PreAuthorize("hasAuthority('employees.attendance')")
    public ResponseEntity<AttendanceRecordDto> createAttendance(
            @PathVariable Long id, @RequestBody AttendanceRecordDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(employeeService.createAttendance(id, dto));
    }

    @PutMapping("/attendance/{attendanceId}")
    @PreAuthorize("hasAuthority('employees.attendance')")
    public ResponseEntity<AttendanceRecordDto> updateAttendance(
            @PathVariable Long attendanceId, @RequestBody AttendanceRecordDto dto) {
        return ResponseEntity.ok(employeeService.updateAttendance(attendanceId, dto));
    }

    @DeleteMapping("/attendance/{attendanceId}")
    @PreAuthorize("hasAuthority('employees.attendance')")
    public ResponseEntity<Void> deleteAttendance(@PathVariable Long attendanceId) {
        employeeService.deleteAttendance(attendanceId);
        return ResponseEntity.noContent().build();
    }

    // ── Comisiones ──────────────────────────────────────────────────────────

    @GetMapping("/{id}/commissions")
    @PreAuthorize("hasAuthority('employees.commissions')")
    public ResponseEntity<CommissionSummaryDto> getCommissions(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(employeeService.getCommissions(id, from, to));
    }

    @GetMapping("/commissions")
    @PreAuthorize("hasAuthority('employees.commissions')")
    public List<CommissionSummaryDto> getAllCommissions(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return employeeService.getAllCommissions(from, to);
    }
}
