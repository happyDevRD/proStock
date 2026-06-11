package com.happydev.prestockbackend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "credit_notes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreditNote {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id", nullable = false)
    private Sale sale;

    @Column(length = 13)
    private String ncf;

    @Column(name = "tipo_comprobante", nullable = false, length = 2)
    private String tipoComprobante = "34";

    @Column(name = "ncf_modificado", length = 13)
    private String ncfModificado;

    @Column(length = 500)
    private String reason;

    @Column(name = "monto_gravado_total", nullable = false, precision = 18, scale = 2)
    private BigDecimal montoGravadoTotal = BigDecimal.ZERO;

    @Column(name = "monto_exento", nullable = false, precision = 18, scale = 2)
    private BigDecimal montoExento = BigDecimal.ZERO;

    @Column(name = "total_itbis", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalItbis = BigDecimal.ZERO;

    @Column(name = "monto_total", nullable = false, precision = 18, scale = 2)
    private BigDecimal montoTotal = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @OneToMany(mappedBy = "creditNote", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CreditNoteItem> items = new ArrayList<>();
}
