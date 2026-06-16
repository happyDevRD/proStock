package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.dto.CreateStockTransferRequest;
import com.happydev.prestockbackend.dto.StockTransferDto;
import com.happydev.prestockbackend.dto.StockTransferItemDto;
import com.happydev.prestockbackend.entity.*;
import com.happydev.prestockbackend.exception.ResourceNotFoundException;
import com.happydev.prestockbackend.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class StockTransferServiceImpl implements StockTransferService {

    private final StockTransferRepository transferRepository;
    private final LocationRepository locationRepository;
    private final ProductRepository productRepository;
    private final StockLocationRepository stockLocationRepository;
    private final UserRepository userRepository;
    private final StockMovementService stockMovementService;

    public StockTransferServiceImpl(StockTransferRepository transferRepository,
                                    LocationRepository locationRepository,
                                    ProductRepository productRepository,
                                    StockLocationRepository stockLocationRepository,
                                    UserRepository userRepository,
                                    StockMovementService stockMovementService) {
        this.transferRepository = transferRepository;
        this.locationRepository = locationRepository;
        this.productRepository = productRepository;
        this.stockLocationRepository = stockLocationRepository;
        this.userRepository = userRepository;
        this.stockMovementService = stockMovementService;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<StockTransferDto> list(int page, int size, String status) {
        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        if (status != null && !status.isBlank()) {
            StockTransferStatus s = StockTransferStatus.valueOf(status.toUpperCase());
            return transferRepository.findByStatusOrderByCreatedAtDesc(s, pageable).map(this::toDto);
        }
        return transferRepository.findAllByOrderByCreatedAtDesc(pageable).map(this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public StockTransferDto findById(Long id) {
        return toDto(transferRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("StockTransfer", "id", id)));
    }

    @Override
    public StockTransferDto create(CreateStockTransferRequest request) {
        if (request.fromLocationId() != null && request.fromLocationId().equals(request.toLocationId())) {
            throw new IllegalArgumentException("El origen y destino no pueden ser la misma ubicación.");
        }

        Location toLocation = locationRepository.findById(request.toLocationId())
                .orElseThrow(() -> new ResourceNotFoundException("Location", "id", request.toLocationId()));
        Location fromLocation = null;
        if (request.fromLocationId() != null) {
            fromLocation = locationRepository.findById(request.fromLocationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Location", "id", request.fromLocationId()));
        }

        StockTransfer transfer = new StockTransfer();
        transfer.setFromLocation(fromLocation);
        transfer.setToLocation(toLocation);
        transfer.setNotes(request.notes());
        transfer.setStatus(StockTransferStatus.DRAFT);

        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        userRepository.findByUsername(username).ifPresent(transfer::setCreatedBy);

        for (var item : request.items()) {
            Product product = productRepository.findById(item.productId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product", "id", item.productId()));
            StockTransferItem ti = new StockTransferItem();
            ti.setTransfer(transfer);
            ti.setProduct(product);
            ti.setQuantity(item.quantity());
            transfer.getItems().add(ti);
        }

        return toDto(transferRepository.save(transfer));
    }

    @Override
    public StockTransferDto complete(Long id) {
        StockTransfer transfer = transferRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("StockTransfer", "id", id));
        if (transfer.getStatus() != StockTransferStatus.DRAFT) {
            throw new IllegalStateException("Solo se pueden completar transferencias en estado DRAFT.");
        }

        LocalDateTime now = LocalDateTime.now();

        for (StockTransferItem item : transfer.getItems()) {
            Product product = item.getProduct();
            int qty = item.getQuantity();

            // Decrementar stock en ubicación origen (si aplica)
            if (transfer.getFromLocation() != null) {
                StockLocation fromSl = stockLocationRepository
                        .findByProductIdAndLocationId(product.getId(), transfer.getFromLocation().getId())
                        .orElseThrow(() -> new IllegalStateException(
                                "Sin stock en origen para: " + product.getName()));
                if (fromSl.getQuantity() < qty) {
                    throw new IllegalStateException(
                            "Stock insuficiente en '" + transfer.getFromLocation().getName()
                                    + "' para: " + product.getName()
                                    + " (disponible: " + fromSl.getQuantity() + ", requerido: " + qty + ")");
                }
                fromSl.setQuantity(fromSl.getQuantity() - qty);
                stockLocationRepository.save(fromSl);
            }

            // Incrementar stock en ubicación destino
            StockLocation toSl = stockLocationRepository
                    .findByProductIdAndLocationId(product.getId(), transfer.getToLocation().getId())
                    .orElseGet(() -> {
                        StockLocation sl = new StockLocation();
                        sl.setProduct(product);
                        sl.setLocation(transfer.getToLocation());
                        sl.setQuantity(0);
                        return sl;
                    });
            toSl.setQuantity(toSl.getQuantity() + qty);
            stockLocationRepository.save(toSl);

            // Registrar movimiento de tipo TRANSFER
            StockMovement movement = new StockMovement();
            movement.setProduct(product);
            movement.setMovementDate(now);
            movement.setQuantityChange(0); // TRANSFER no cambia stock total
            movement.setType(StockMovementType.TRANSFER);
            movement.setReason("Transferencia #" + id + " — "
                    + (transfer.getFromLocation() != null ? transfer.getFromLocation().getName() : "Externo")
                    + " → " + transfer.getToLocation().getName());
            if (transfer.getFromLocation() != null) {
                movement.setSourceLocationId(transfer.getFromLocation().getId());
            }
            movement.setDestinationLocationId(transfer.getToLocation().getId());
            stockMovementService.createMovement(movement);
        }

        transfer.setStatus(StockTransferStatus.COMPLETED);
        transfer.setCompletedAt(now);
        return toDto(transferRepository.save(transfer));
    }

    @Override
    public StockTransferDto cancel(Long id) {
        StockTransfer transfer = transferRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("StockTransfer", "id", id));
        if (transfer.getStatus() != StockTransferStatus.DRAFT) {
            throw new IllegalStateException("Solo se pueden cancelar transferencias en estado DRAFT.");
        }
        transfer.setStatus(StockTransferStatus.CANCELED);
        return toDto(transferRepository.save(transfer));
    }

    @Override
    public void delete(Long id) {
        StockTransfer transfer = transferRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("StockTransfer", "id", id));
        if (transfer.getStatus() != StockTransferStatus.DRAFT) {
            throw new IllegalStateException("Solo se pueden eliminar transferencias en estado DRAFT.");
        }
        transferRepository.delete(transfer);
    }

    private StockTransferDto toDto(StockTransfer t) {
        List<StockTransferItemDto> items = t.getItems().stream()
                .map(i -> new StockTransferItemDto(
                        i.getId(),
                        i.getProduct().getId(),
                        i.getProduct().getName(),
                        i.getProduct().getSku(),
                        i.getQuantity()))
                .toList();
        return new StockTransferDto(
                t.getId(),
                t.getFromLocation() != null ? t.getFromLocation().getId() : null,
                t.getFromLocation() != null ? t.getFromLocation().getName() : null,
                t.getToLocation().getId(),
                t.getToLocation().getName(),
                t.getStatus(),
                t.getNotes(),
                t.getCreatedBy() != null
                        ? t.getCreatedBy().getFirstName() + " " + t.getCreatedBy().getLastName()
                        : null,
                t.getCreatedAt(),
                t.getCompletedAt(),
                items);
    }
}
