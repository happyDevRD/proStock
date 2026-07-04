package com.happydev.prestockbackend.dto;

import com.happydev.prestockbackend.entity.AttendanceType;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
public class AttendanceRecordDto {
    private Long id;
    private Long employeeId;
    private String employeeName;
    private LocalDate fecha;
    private LocalTime horaEntrada;
    private LocalTime horaSalida;
    private AttendanceType tipo;
    private String notas;
    private LocalDateTime createdAt;
}
