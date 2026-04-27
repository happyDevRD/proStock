package com.happydev.prestockbackend.entity;

import com.happydev.prestockbackend.entity.converter.TipoIngresosConverter;
import jakarta.persistence.*;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "sales")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Sale {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sale_date", nullable = false)
    private LocalDateTime saleDate; // Fecha y hora de la venta

    @ManyToOne(fetch = FetchType.LAZY) // Relación con Customer
    @JoinColumn(name = "customer_id") //  Puede ser nullable si permites ventas sin cliente
    private Customer customer;


    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SaleItem> items = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false) //Por defecto que sea PENDING
    private SaleStatus status = SaleStatus.PENDING;

    @Column(name = "tipo_comprobante", length = 2)
    @Pattern(regexp = "^\\d{2}$", message = "El tipo de comprobante debe tener 2 digitos")
    private String tipoComprobante;

    @Column(name = "ncf", length = 13, unique = true)
    private String ncf;

    @Column(name = "monto_gravado_total", nullable = false, precision = 18, scale = 2)
    private BigDecimal montoGravadoTotal = BigDecimal.ZERO;

    @Column(name = "monto_exento", nullable = false, precision = 18, scale = 2)
    private BigDecimal montoExento = BigDecimal.ZERO;

    @Column(name = "total_itbis", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalItbis = BigDecimal.ZERO;

    @Column(name = "monto_total", nullable = false, precision = 18, scale = 2)
    private BigDecimal montoTotal = BigDecimal.ZERO;

    @Convert(converter = TipoIngresosConverter.class)
    @Column(name = "tipo_ingresos", nullable = false, length = 2)
    private TipoIngresos tipoIngresos = TipoIngresos.OPERACIONES;

    @Column(name = "fecha_firma")
    private LocalDateTime fechaFirma;

    @Column(name = "codigo_seguridad", length = 64)
    private String codigoSeguridad;

    @Column(name = "qr_payload_url", length = 1000)
    private String qrPayloadUrl;

    @Column(name = "qr_code_base64", columnDefinition = "TEXT")
    private String qrCodeBase64;
}
