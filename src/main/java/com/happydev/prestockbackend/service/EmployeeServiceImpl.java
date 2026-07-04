package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.dto.AttendanceRecordDto;
import com.happydev.prestockbackend.dto.CommissionSummaryDto;
import com.happydev.prestockbackend.dto.EmployeeDto;
import com.happydev.prestockbackend.entity.*;
import com.happydev.prestockbackend.exception.ResourceNotFoundException;
import com.happydev.prestockbackend.repository.AttendanceRecordRepository;
import com.happydev.prestockbackend.repository.EmployeeRepository;
import com.happydev.prestockbackend.repository.SaleRepository;
import com.happydev.prestockbackend.util.SecurityAuditUtils;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class EmployeeServiceImpl implements EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final AttendanceRecordRepository attendanceRepository;
    private final SaleRepository saleRepository;

    public EmployeeServiceImpl(EmployeeRepository employeeRepository,
                               AttendanceRecordRepository attendanceRepository,
                               SaleRepository saleRepository) {
        this.employeeRepository = employeeRepository;
        this.attendanceRepository = attendanceRepository;
        this.saleRepository = saleRepository;
    }

    // ── Empleados ───────────────────────────────────────────────────────────

    @Override
    public Page<EmployeeDto> findAll(String search, String status, String departamento, Pageable pageable) {
        String searchFilter = (search == null || search.isBlank()) ? null : search;
        EmployeeStatus statusFilter = null;
        if (status != null && !status.isBlank()) {
            try { statusFilter = EmployeeStatus.valueOf(status.toUpperCase()); }
            catch (IllegalArgumentException ignored) {}
        }
        EmployeeDepartment deptFilter = null;
        if (departamento != null && !departamento.isBlank()) {
            try { deptFilter = EmployeeDepartment.valueOf(departamento.toUpperCase()); }
            catch (IllegalArgumentException ignored) {}
        }
        return employeeRepository.findFiltered(searchFilter, statusFilter, deptFilter, pageable).map(this::toDto);
    }

    @Override
    public Optional<EmployeeDto> findById(@NonNull Long id) {
        return employeeRepository.findById(id).map(this::toDto);
    }

    @Override
    public List<EmployeeDto> findAllActive() {
        return employeeRepository.findByStatusOrderByNombreCompletoAsc(EmployeeStatus.ACTIVO)
                .stream().map(this::toDto).toList();
    }

    @Override
    public EmployeeDto create(@NonNull EmployeeDto dto) {
        Employee emp = fromDto(new Employee(), dto);
        emp.setCreatedBy(SecurityAuditUtils.currentUsernameOrNull());
        return toDto(employeeRepository.save(emp));
    }

    @Override
    public EmployeeDto update(@NonNull Long id, @NonNull EmployeeDto dto) {
        Employee emp = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", "id", id));
        return toDto(employeeRepository.save(fromDto(emp, dto)));
    }

    @Override
    public void delete(@NonNull Long id) {
        if (!employeeRepository.existsById(id)) {
            throw new ResourceNotFoundException("Employee", "id", id);
        }
        employeeRepository.deleteById(id);
    }

    // ── Asistencia ──────────────────────────────────────────────────────────

    @Override
    public List<AttendanceRecordDto> findAttendance(Long employeeId, LocalDate from, LocalDate to) {
        return attendanceRepository.findByEmployee(employeeId, from, to)
                .stream().map(this::toAttendanceDto).toList();
    }

    @Override
    public List<AttendanceRecordDto> findAllAttendance(LocalDate from, LocalDate to) {
        return attendanceRepository.findAll(from, to)
                .stream().map(this::toAttendanceDto).toList();
    }

    @Override
    public AttendanceRecordDto createAttendance(@NonNull Long employeeId, @NonNull AttendanceRecordDto dto) {
        Employee emp = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", "id", employeeId));
        AttendanceRecord record = fromAttendanceDto(new AttendanceRecord(), dto);
        record.setEmployee(emp);
        return toAttendanceDto(attendanceRepository.save(record));
    }

    @Override
    public AttendanceRecordDto updateAttendance(@NonNull Long attendanceId, @NonNull AttendanceRecordDto dto) {
        AttendanceRecord record = attendanceRepository.findById(attendanceId)
                .orElseThrow(() -> new ResourceNotFoundException("AttendanceRecord", "id", attendanceId));
        return toAttendanceDto(attendanceRepository.save(fromAttendanceDto(record, dto)));
    }

    @Override
    public void deleteAttendance(@NonNull Long attendanceId) {
        if (!attendanceRepository.existsById(attendanceId)) {
            throw new ResourceNotFoundException("AttendanceRecord", "id", attendanceId);
        }
        attendanceRepository.deleteById(attendanceId);
    }

    // ── Comisiones ──────────────────────────────────────────────────────────

    @Override
    public CommissionSummaryDto getCommissions(@NonNull Long employeeId, LocalDate from, LocalDate to) {
        Employee emp = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", "id", employeeId));
        return buildCommissionSummary(emp, from, to);
    }

    @Override
    public List<CommissionSummaryDto> getAllCommissions(LocalDate from, LocalDate to) {
        return employeeRepository.findByStatusOrderByNombreCompletoAsc(EmployeeStatus.ACTIVO)
                .stream()
                .map(e -> buildCommissionSummary(e, from, to))
                .toList();
    }

    private CommissionSummaryDto buildCommissionSummary(Employee emp, LocalDate from, LocalDate to) {
        LocalDateTime fromDt = from != null ? from.atStartOfDay() : LocalDateTime.of(2000, 1, 1, 0, 0);
        LocalDateTime toDt = to != null ? to.atTime(23, 59, 59) : LocalDateTime.now();

        List<com.happydev.prestockbackend.entity.Sale> sales =
                saleRepository.findByEmployeeIdAndDateRange(emp.getId(), fromDt, toDt);

        BigDecimal totalVentas = sales.stream()
                .map(com.happydev.prestockbackend.entity.Sale::getMontoTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal pct = emp.getComisionPorcentaje() != null ? emp.getComisionPorcentaje() : BigDecimal.ZERO;
        BigDecimal montoComision = totalVentas
                .multiply(pct)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        return new CommissionSummaryDto(
                emp.getId(),
                emp.getNombreCompleto(),
                pct,
                totalVentas,
                montoComision,
                sales.size(),
                from != null ? from.toString() : null,
                to != null ? to.toString() : null
        );
    }

    // ── Mappers internos ────────────────────────────────────────────────────

    private Employee fromDto(Employee emp, EmployeeDto dto) {
        emp.setNombreCompleto(dto.getNombreCompleto());
        emp.setCedula(dto.getCedula());
        emp.setTelefono(dto.getTelefono());
        emp.setEmail(dto.getEmail());
        emp.setCargo(dto.getCargo());
        emp.setDepartamento(dto.getDepartamento() != null ? dto.getDepartamento() : EmployeeDepartment.OTRO);
        emp.setTipoEmpleo(dto.getTipoEmpleo() != null ? dto.getTipoEmpleo() : EmployeeType.PERMANENTE);
        emp.setFechaIngreso(dto.getFechaIngreso());
        emp.setFechaSalida(dto.getFechaSalida());
        emp.setSalarioBase(dto.getSalarioBase() != null ? dto.getSalarioBase() : BigDecimal.ZERO);
        emp.setTipoSalario(dto.getTipoSalario() != null ? dto.getTipoSalario() : EmployeeSalaryType.MENSUAL);
        emp.setComisionPorcentaje(dto.getComisionPorcentaje() != null ? dto.getComisionPorcentaje() : BigDecimal.ZERO);
        emp.setNumTss(dto.getNumTss());
        emp.setBanco(dto.getBanco());
        emp.setCuentaBancaria(dto.getCuentaBancaria());
        emp.setStatus(dto.getStatus() != null ? dto.getStatus() : EmployeeStatus.ACTIVO);
        emp.setFotoUrl(dto.getFotoUrl());
        emp.setNotas(dto.getNotas());
        return emp;
    }

    private EmployeeDto toDto(Employee emp) {
        EmployeeDto dto = new EmployeeDto();
        dto.setId(emp.getId());
        dto.setNombreCompleto(emp.getNombreCompleto());
        dto.setCedula(emp.getCedula());
        dto.setTelefono(emp.getTelefono());
        dto.setEmail(emp.getEmail());
        dto.setCargo(emp.getCargo());
        dto.setDepartamento(emp.getDepartamento());
        dto.setTipoEmpleo(emp.getTipoEmpleo());
        dto.setFechaIngreso(emp.getFechaIngreso());
        dto.setFechaSalida(emp.getFechaSalida());
        dto.setSalarioBase(emp.getSalarioBase());
        dto.setTipoSalario(emp.getTipoSalario());
        dto.setComisionPorcentaje(emp.getComisionPorcentaje());
        dto.setNumTss(emp.getNumTss());
        dto.setBanco(emp.getBanco());
        dto.setCuentaBancaria(emp.getCuentaBancaria());
        dto.setStatus(emp.getStatus());
        dto.setFotoUrl(emp.getFotoUrl());
        dto.setNotas(emp.getNotas());
        dto.setCreatedBy(emp.getCreatedBy());
        dto.setCreatedAt(emp.getCreatedAt());
        return dto;
    }

    private AttendanceRecord fromAttendanceDto(AttendanceRecord record, AttendanceRecordDto dto) {
        record.setFecha(dto.getFecha());
        record.setHoraEntrada(dto.getHoraEntrada());
        record.setHoraSalida(dto.getHoraSalida());
        record.setTipo(dto.getTipo() != null ? dto.getTipo() : AttendanceType.NORMAL);
        record.setNotas(dto.getNotas());
        return record;
    }

    private AttendanceRecordDto toAttendanceDto(AttendanceRecord record) {
        AttendanceRecordDto dto = new AttendanceRecordDto();
        dto.setId(record.getId());
        if (record.getEmployee() != null) {
            dto.setEmployeeId(record.getEmployee().getId());
            dto.setEmployeeName(record.getEmployee().getNombreCompleto());
        }
        dto.setFecha(record.getFecha());
        dto.setHoraEntrada(record.getHoraEntrada());
        dto.setHoraSalida(record.getHoraSalida());
        dto.setTipo(record.getTipo());
        dto.setNotas(record.getNotas());
        dto.setCreatedAt(record.getCreatedAt());
        return dto;
    }
}
