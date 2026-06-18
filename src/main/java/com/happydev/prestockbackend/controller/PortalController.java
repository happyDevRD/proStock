package com.happydev.prestockbackend.controller;

import com.happydev.prestockbackend.entity.*;
import com.happydev.prestockbackend.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/portal")
@PreAuthorize("hasRole('CUSTOMER')")
public class PortalController {

    private final CustomerRepository customerRepository;
    private final SaleRepository saleRepository;
    private final ServiceOrderRepository serviceOrderRepository;
    private final SalePaymentRepository salePaymentRepository;
    private final ServiceOrderStageRepository serviceOrderStageRepository;
    private final CompanyConfigRepository companyConfigRepository;
    private final PasswordEncoder passwordEncoder;

    public PortalController(
            CustomerRepository customerRepository,
            SaleRepository saleRepository,
            ServiceOrderRepository serviceOrderRepository,
            SalePaymentRepository salePaymentRepository,
            ServiceOrderStageRepository serviceOrderStageRepository,
            CompanyConfigRepository companyConfigRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.customerRepository = customerRepository;
        this.saleRepository = saleRepository;
        this.serviceOrderRepository = serviceOrderRepository;
        this.salePaymentRepository = salePaymentRepository;
        this.serviceOrderStageRepository = serviceOrderStageRepository;
        this.companyConfigRepository = companyConfigRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** Información pública de la empresa (logo, nombre, contacto). No requiere auth. */
    @GetMapping("/company-info")
    @PreAuthorize("permitAll()")
    public PortalCompanyInfoDto companyInfo() {
        return companyConfigRepository.findFirstByOrderByIdAsc()
                .map(c -> new PortalCompanyInfoDto(
                        c.getRazonSocial(),
                        c.getNombreComercial(),
                        c.getRnc(),
                        c.getDireccion(),
                        c.getNumeroTelefono(),
                        c.getCorreoElectronico(),
                        c.getLogoFileName() != null ? "/uploads/" + c.getLogoFileName() : null,
                        c.getInvoiceFooterText()
                ))
                .orElse(new PortalCompanyInfoDto(null, null, null, null, null, null, null, null));
    }

    /** Perfil del cliente autenticado. */
    @GetMapping("/me")
    public PortalCustomerDto me(java.security.Principal principal) {
        Customer c = loadCustomer(principal);
        return new PortalCustomerDto(c.getId(), c.getFirstName(), c.getLastName(), c.getEmail(),
                c.getPhoneNumber(), c.getPortalLastLogin());
    }

    /** Facturas del cliente con filtros opcionales. */
    @GetMapping("/invoices")
    public PortalPageDto<PortalInvoiceDto> invoices(
            java.security.Principal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) String ncf
    ) {
        Long customerId = resolveCustomerId(principal);
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "saleDate"));

        Specification<Sale> spec = (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(cb.equal(root.get("customer").get("id"), customerId));
            if (status != null && !status.isBlank()) {
                try {
                    predicates.add(cb.equal(root.get("status"), SaleStatus.valueOf(status)));
                } catch (IllegalArgumentException ignored) {}
            } else {
                predicates.add(root.get("status").in(
                        SaleStatus.PENDING, SaleStatus.PARTIALLY_PAID, SaleStatus.COMPLETED));
            }
            if (dateFrom != null && !dateFrom.isBlank()) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("saleDate"),
                        LocalDate.parse(dateFrom).atStartOfDay()));
            }
            if (dateTo != null && !dateTo.isBlank()) {
                predicates.add(cb.lessThan(root.get("saleDate"),
                        LocalDate.parse(dateTo).plusDays(1).atStartOfDay()));
            }
            if (ncf != null && !ncf.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("ncf")), "%" + ncf.toLowerCase() + "%"));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        Page<Sale> result = saleRepository.findAll(spec, pageable);
        return new PortalPageDto<>(
                result.getContent().stream().map(this::toInvoiceDto).toList(),
                result.getTotalPages(),
                result.getTotalElements(),
                result.getNumber()
        );
    }

    /** Detalle de una factura (solo si pertenece al cliente). */
    @GetMapping("/invoices/{id}")
    public PortalInvoiceDetailDto invoiceDetail(java.security.Principal principal, @PathVariable Long id) {
        Long customerId = resolveCustomerId(principal);
        Sale sale = saleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (sale.getCustomer() == null || !sale.getCustomer().getId().equals(customerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return toInvoiceDetailDto(sale);
    }

    /** Órdenes de servicio del cliente. */
    @GetMapping("/service-orders")
    public List<PortalServiceOrderDto> serviceOrders(java.security.Principal principal) {
        Long customerId = resolveCustomerId(principal);
        return serviceOrderRepository.findByCustomerIdOrderByCreatedAtDesc(customerId)
                .stream().map(this::toServiceOrderDto).toList();
    }

    /** Detalle de una orden de servicio (dispositivo + etapas). */
    @GetMapping("/service-orders/{id}")
    public PortalServiceOrderDetailDto serviceOrderDetail(
            java.security.Principal principal, @PathVariable Long id) {
        Long customerId = resolveCustomerId(principal);
        ServiceOrder order = serviceOrderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (order.getCustomer() == null || !order.getCustomer().getId().equals(customerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        List<PortalStageDto> stages = serviceOrderStageRepository
                .findByServiceOrderIdOrderByEnteredAtAsc(id)
                .stream().map(s -> new PortalStageDto(
                        s.getStageName(),
                        s.getEnteredAt() != null ? s.getEnteredAt().toString() : null,
                        s.getNotes(),
                        s.getActor()
                )).toList();
        return new PortalServiceOrderDetailDto(
                order.getId(), order.getOrderNumber(), order.getTitle(),
                order.getOrderType().name(), order.getStatus().name(), order.getCurrentStage(),
                order.getAppointmentDate(), order.getEstimatedDelivery(),
                order.getEstimatedAmount(), order.getDepositAmount(), order.getProblemDescription(),
                order.getDeviceBrand(), order.getDeviceModel(),
                order.getDeviceSerial(), order.getDeviceCondition(),
                order.isBudgetApproved(),
                order.getBudgetApprovedAt() != null ? order.getBudgetApprovedAt().toString() : null,
                stages
        );
    }

    /** Resumen de cuenta: balance pendiente, total pagado, número de facturas. */
    @GetMapping("/statement")
    public PortalStatementDto statement(java.security.Principal principal) {
        Long customerId = resolveCustomerId(principal);
        var invoices = saleRepository.findAll(
                (root, query, cb) -> cb.and(
                        cb.equal(root.get("customer").get("id"), customerId),
                        root.get("status").in(SaleStatus.PENDING, SaleStatus.PARTIALLY_PAID, SaleStatus.COMPLETED)
                )
        );
        BigDecimal totalCharged = invoices.stream()
                .map(Sale::getMontoTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPaid = invoices.stream()
                .map(Sale::getPaidAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        long openCount = invoices.stream()
                .filter(s -> s.getStatus() == SaleStatus.PENDING || s.getStatus() == SaleStatus.PARTIALLY_PAID)
                .count();
        return new PortalStatementDto(totalCharged, totalPaid, totalCharged.subtract(totalPaid), (int) openCount);
    }

    /** Cambio de contraseña del portal por el propio cliente. */
    @PostMapping("/change-password")
    public void changePassword(
            java.security.Principal principal,
            @RequestBody PortalChangePasswordRequest req
    ) {
        Customer customer = loadCustomer(principal);
        if (customer.getPortalPassword() == null
                || !passwordEncoder.matches(req.currentPassword(), customer.getPortalPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Contraseña actual incorrecta");
        }
        if (req.newPassword() == null || req.newPassword().length() < 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La nueva contraseña debe tener al menos 6 caracteres");
        }
        customer.setPortalPassword(passwordEncoder.encode(req.newPassword()));
        customerRepository.save(customer);
    }

    // --- helpers ---

    private Long resolveCustomerId(java.security.Principal principal) {
        try {
            return Long.parseLong(principal.getName());
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
    }

    private Customer loadCustomer(java.security.Principal principal) {
        Long id = resolveCustomerId(principal);
        return customerRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private PortalInvoiceDto toInvoiceDto(Sale s) {
        return new PortalInvoiceDto(
                s.getId(), s.getSaleDate(), s.getNcf(), s.getStatus().name(),
                s.getMontoTotal(), s.getPaidAmount(),
                s.getMontoTotal().subtract(s.getPaidAmount()),
                s.getDueDate()
        );
    }

    private PortalInvoiceDetailDto toInvoiceDetailDto(Sale s) {
        var items = s.getItems().stream().map(i -> new PortalInvoiceItemDto(
                i.getProductName(), i.getQuantity(), i.getUnitPrice(),
                i.getUnitPrice().multiply(new BigDecimal(i.getQuantity()))
        )).toList();
        var payments = salePaymentRepository.findBySaleIdOrderByPaymentDateAsc(s.getId())
                .stream().map(p -> new PortalPaymentDto(
                        p.getAmount(),
                        p.getPaymentMethod() != null ? p.getPaymentMethod().name() : null,
                        p.getPaymentDate() != null ? p.getPaymentDate().toString() : null,
                        p.getNotes()
                )).toList();
        return new PortalInvoiceDetailDto(
                s.getId(), s.getSaleDate(), s.getNcf(), s.getStatus().name(),
                s.getMontoGravadoTotal(), s.getTotalItbis(), s.getMontoTotal(),
                s.getPaidAmount(), s.getMontoTotal().subtract(s.getPaidAmount()),
                s.getDueDate(),
                s.getDiscountAmount(),
                s.getMontoExento(),
                s.getQrCodeBase64(),
                s.getPaymentMethod() != null ? s.getPaymentMethod().name() : null,
                items, payments
        );
    }

    private PortalServiceOrderDto toServiceOrderDto(ServiceOrder o) {
        return new PortalServiceOrderDto(
                o.getId(), o.getOrderNumber(), o.getTitle(), o.getOrderType().name(),
                o.getStatus().name(), o.getCurrentStage(),
                o.getAppointmentDate(), o.getEstimatedDelivery(),
                o.getEstimatedAmount(), o.getDepositAmount(),
                o.getProblemDescription(),
                o.getDeviceBrand(), o.getDeviceModel(),
                o.getDeviceSerial(), o.getDeviceCondition(),
                o.isBudgetApproved(),
                o.getBudgetApprovedAt() != null ? o.getBudgetApprovedAt().toString() : null
        );
    }

    // --- DTOs de respuesta ---

    public record PortalCompanyInfoDto(
            String razonSocial, String nombreComercial, String rnc,
            String direccion, String telefono, String email,
            String logoUrl, String invoiceFooterText
    ) {}

    public record PortalCustomerDto(
            Long id, String firstName, String lastName,
            String email, String phoneNumber, LocalDateTime lastLogin
    ) {}

    public record PortalPageDto<T>(
            List<T> content, int totalPages, long totalElements, int currentPage
    ) {}

    public record PortalInvoiceDto(
            Long id, LocalDateTime saleDate, String ncf, String status,
            BigDecimal total, BigDecimal paid, BigDecimal balance, LocalDate dueDate
    ) {}

    public record PortalInvoiceDetailDto(
            Long id, LocalDateTime saleDate, String ncf, String status,
            BigDecimal subtotal, BigDecimal itbis, BigDecimal total,
            BigDecimal paid, BigDecimal balance, LocalDate dueDate,
            BigDecimal discountAmount, BigDecimal montoExento,
            String qrCodeBase64, String paymentMethod,
            List<PortalInvoiceItemDto> items,
            List<PortalPaymentDto> payments
    ) {}

    public record PortalInvoiceItemDto(
            String productName, int quantity, BigDecimal unitPrice, BigDecimal subtotal
    ) {}

    public record PortalPaymentDto(
            BigDecimal amount, String paymentMethod, String paymentDate, String notes
    ) {}

    public record PortalServiceOrderDto(
            Long id, String orderNumber, String title, String orderType,
            String status, String currentStage,
            LocalDateTime appointmentDate, LocalDate estimatedDelivery,
            BigDecimal estimatedAmount, BigDecimal depositAmount,
            String problemDescription,
            String deviceBrand, String deviceModel,
            String deviceSerial, String deviceCondition,
            boolean budgetApproved, String budgetApprovedAt
    ) {}

    public record PortalServiceOrderDetailDto(
            Long id, String orderNumber, String title, String orderType,
            String status, String currentStage,
            LocalDateTime appointmentDate, LocalDate estimatedDelivery,
            BigDecimal estimatedAmount, BigDecimal depositAmount,
            String problemDescription,
            String deviceBrand, String deviceModel,
            String deviceSerial, String deviceCondition,
            boolean budgetApproved, String budgetApprovedAt,
            List<PortalStageDto> stages
    ) {}

    public record PortalStageDto(
            String stageName, String enteredAt, String notes, String actor
    ) {}

    public record PortalStatementDto(
            BigDecimal totalCharged, BigDecimal totalPaid,
            BigDecimal balance, int openInvoices
    ) {}

    public record PortalChangePasswordRequest(String currentPassword, String newPassword) {}
}
