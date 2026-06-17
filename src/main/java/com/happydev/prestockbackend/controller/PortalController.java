package com.happydev.prestockbackend.controller;

import com.happydev.prestockbackend.entity.Customer;
import com.happydev.prestockbackend.entity.Sale;
import com.happydev.prestockbackend.entity.SaleStatus;
import com.happydev.prestockbackend.entity.ServiceOrder;
import com.happydev.prestockbackend.repository.CustomerRepository;
import com.happydev.prestockbackend.repository.SaleRepository;
import com.happydev.prestockbackend.repository.ServiceOrderRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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

    public PortalController(
            CustomerRepository customerRepository,
            SaleRepository saleRepository,
            ServiceOrderRepository serviceOrderRepository
    ) {
        this.customerRepository = customerRepository;
        this.saleRepository = saleRepository;
        this.serviceOrderRepository = serviceOrderRepository;
    }

    /** Perfil del cliente autenticado. */
    @GetMapping("/me")
    public PortalCustomerDto me(java.security.Principal principal) {
        Customer c = loadCustomer(principal);
        return new PortalCustomerDto(c.getId(), c.getFirstName(), c.getLastName(), c.getEmail(),
                c.getPhoneNumber(), c.getPortalLastLogin());
    }

    /** Facturas del cliente (PENDING, PARTIALLY_PAID y COMPLETED). */
    @GetMapping("/invoices")
    public List<PortalInvoiceDto> invoices(
            java.security.Principal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Long customerId = resolveCustomerId(principal);
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "saleDate"));
        return saleRepository.findAll(
                (root, query, cb) -> cb.and(
                        cb.equal(root.get("customer").get("id"), customerId),
                        root.get("status").in(
                                SaleStatus.PENDING, SaleStatus.PARTIALLY_PAID, SaleStatus.COMPLETED)
                ),
                pageable
        ).getContent().stream().map(this::toInvoiceDto).toList();
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
                i.getUnitPrice().multiply(new java.math.BigDecimal(i.getQuantity()))
        )).toList();
        return new PortalInvoiceDetailDto(
                s.getId(), s.getSaleDate(), s.getNcf(), s.getStatus().name(),
                s.getMontoGravadoTotal(), s.getTotalItbis(), s.getMontoTotal(),
                s.getPaidAmount(), s.getMontoTotal().subtract(s.getPaidAmount()),
                s.getDueDate(), items
        );
    }

    private PortalServiceOrderDto toServiceOrderDto(ServiceOrder o) {
        return new PortalServiceOrderDto(
                o.getId(), o.getOrderNumber(), o.getTitle(), o.getOrderType().name(),
                o.getStatus().name(), o.getCurrentStage(),
                o.getAppointmentDate(), o.getEstimatedDelivery(),
                o.getEstimatedAmount(), o.getDepositAmount(),
                o.getProblemDescription()
        );
    }

    // --- DTOs de respuesta ---

    public record PortalCustomerDto(
            Long id, String firstName, String lastName,
            String email, String phoneNumber, LocalDateTime lastLogin
    ) {}

    public record PortalInvoiceDto(
            Long id, LocalDateTime saleDate, String ncf, String status,
            BigDecimal total, BigDecimal paid, BigDecimal balance, LocalDate dueDate
    ) {}

    public record PortalInvoiceDetailDto(
            Long id, LocalDateTime saleDate, String ncf, String status,
            BigDecimal subtotal, BigDecimal itbis, BigDecimal total,
            BigDecimal paid, BigDecimal balance, LocalDate dueDate,
            List<PortalInvoiceItemDto> items
    ) {}

    public record PortalInvoiceItemDto(
            String productName, int quantity, BigDecimal unitPrice, BigDecimal subtotal
    ) {}

    public record PortalServiceOrderDto(
            Long id, String orderNumber, String title, String orderType,
            String status, String currentStage,
            LocalDateTime appointmentDate, LocalDate estimatedDelivery,
            BigDecimal estimatedAmount, BigDecimal depositAmount,
            String problemDescription
    ) {}

    public record PortalStatementDto(
            BigDecimal totalCharged, BigDecimal totalPaid,
            BigDecimal balance, int openInvoices
    ) {}
}
