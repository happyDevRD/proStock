package com.happydev.prestockbackend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(
        name = "ncf_sequences",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_ncf_sequence_tipo", columnNames = "tipo_comprobante")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NcfSequence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tipo_comprobante", nullable = false, length = 2)
    @NotBlank
    @Pattern(regexp = "^\\d{2}$", message = "El tipo de comprobante debe tener 2 digitos (ej. 31, 32, 34)")
    private String tipoComprobante;

    @Column(nullable = false, length = 1)
    @NotBlank
    @Pattern(regexp = "^[BE]$", message = "El prefijo debe ser B (papel) o E (electronico)")
    private String prefijo;

    @Column(name = "valor_actual", nullable = false)
    @NotNull
    private Long valorActual;

    @Column(name = "valor_final", nullable = false)
    @NotNull
    private Long valorFinal;

    @Column(name = "fecha_vencimiento", nullable = false)
    @NotNull
    private LocalDate fechaVencimiento;
}
